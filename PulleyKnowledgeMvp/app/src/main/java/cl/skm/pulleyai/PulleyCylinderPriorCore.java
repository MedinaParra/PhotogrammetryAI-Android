package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Fits an arbitrary-scale sparse cloud to a known pulley shell cylinder without
 * claiming that the prior-constrained model is an independent measurement.
 */
public final class PulleyCylinderPriorCore {
    private static final double EPS = 1e-10;

    private PulleyCylinderPriorCore() {}

    public static Result fit(List<PointCloudExportCore.Point> input,
                             double shellLengthMm, double shellDiameterMm) {
        if (!(shellLengthMm > 0.0) || !(shellDiameterMm > 0.0)
                || !Double.isFinite(shellLengthMm) || !Double.isFinite(shellDiameterMm)) {
            return Result.blocked("MISSING_DIMENSION_PRIOR", shellLengthMm, shellDiameterMm);
        }
        List<Sample> samples = qualityFiltered(input);
        if (samples.size() < 12) {
            return Result.blocked("INSUFFICIENT_FINITE_POINTS", shellLengthMm, shellDiameterMm);
        }

        double[] robustCenter = coordinateMedian(samples);
        samples = distanceTrim(samples, robustCenter, 0.98, 1.6);
        if (samples.size() < 12) {
            return Result.blocked("INSUFFICIENT_POINTS_AFTER_TRIM", shellLengthMm, shellDiameterMm);
        }

        double[] mean = mean(samples);
        double[][] covariance = covariance(samples, mean);
        Eigen eigen = jacobi(covariance);
        Candidate best = null;
        for (int column = 0; column < 3; column++) {
            double[] axis = normalize(new double[]{
                    eigen.vectors[0][column], eigen.vectors[1][column], eigen.vectors[2][column]});
            Candidate candidate = evaluate(samples, mean, axis, shellLengthMm, shellDiameterMm);
            if (candidate != null && (best == null || candidate.score < best.score)) best = candidate;
        }
        if (best == null) {
            return Result.blocked("CYLINDER_AXIS_UNOBSERVABLE", shellLengthMm, shellDiameterMm);
        }

        List<AlignedPoint> aligned = new ArrayList<AlignedPoint>(samples.size());
        double radiusMm = shellDiameterMm * 0.5;
        double radialTolerance = Math.max(5.0, radiusMm * 0.10);
        double axialTolerance = Math.max(8.0, shellLengthMm * 0.04);
        int inliers = 0;
        double sumSquared = 0.0;
        List<Double> inlierAxial = new ArrayList<Double>();
        for (Sample sample : samples) {
            double[] d = subtract(sample.xyz, best.axisOrigin);
            double x = (dot(d, best.axis) - best.axialMidRaw) * best.scaleMmPerRaw;
            double y = dot(d, best.radialU) * best.scaleMmPerRaw;
            double z = dot(d, best.radialV) * best.scaleMmPerRaw;
            double radial = Math.hypot(y, z);
            double radialResidual = Math.abs(radial - radiusMm);
            double axialOverrun = Math.max(0.0, Math.abs(x) - shellLengthMm * 0.5);
            boolean inlier = radialResidual <= radialTolerance && axialOverrun <= axialTolerance;
            if (inlier) {
                inliers++;
                sumSquared += radialResidual * radialResidual;
                inlierAxial.add(x);
            }
            aligned.add(new AlignedPoint(x, y, z, inlier,
                    radialResidual, sample.errorPx, sample.parallaxDegrees));
        }
        double inlierRatio = aligned.isEmpty() ? 0.0 : inliers / (double) aligned.size();
        double radialRms = inliers > 0 ? Math.sqrt(sumSquared / inliers) : Double.POSITIVE_INFINITY;
        double axialCoverage = coverage(inlierAxial, shellLengthMm);
        boolean accepted = inliers >= 20 && inlierRatio >= 0.50
                && radialRms <= Math.max(8.0, radiusMm * 0.10)
                && best.scaleConsistency <= 0.35 && axialCoverage >= 0.25;
        boolean weak = !accepted && inliers >= 10 && inlierRatio >= 0.25
                && best.scaleConsistency <= 0.85;
        String status = accepted ? "FIT_ACCEPTED"
                : weak ? "FIT_WEAK" : "PRIOR_ONLY";
        return new Result(true, accepted, status, shellLengthMm, shellDiameterMm,
                input == null ? 0 : input.size(), samples.size(), inliers, inlierRatio,
                radialRms, axialCoverage, best.scaleMmPerRaw, best.scaleConsistency,
                best.axis, best.axisOrigin, aligned);
    }

    private static Candidate evaluate(List<Sample> samples, double[] mean, double[] inputAxis,
                                      double lengthMm, double diameterMm) {
        double[] axis = canonicalAxis(inputAxis);
        double[] radialU = perpendicular(axis);
        double[] radialV = normalize(cross(axis, radialU));
        double[] axial = new double[samples.size()];
        double[] u = new double[samples.size()];
        double[] v = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            double[] d = subtract(samples.get(i).xyz, mean);
            axial[i] = dot(d, axis);
            u[i] = dot(d, radialU);
            v[i] = dot(d, radialV);
        }
        Circle circle = fitCircleRobust(u, v);
        if (circle == null || !(circle.radius > EPS)) return null;
        double p05 = percentile(axial, 0.05);
        double p95 = percentile(axial, 0.95);
        double span = p95 - p05;
        if (!(span > EPS)) return null;
        double scaleRadial = (diameterMm * 0.5) / circle.radius;
        double scaleAxial = lengthMm / span;
        if (!(scaleRadial > EPS) || !(scaleAxial > EPS)
                || !Double.isFinite(scaleRadial) || !Double.isFinite(scaleAxial)) return null;
        double consistency = Math.abs(Math.log(scaleRadial / scaleAxial));
        double scale = Math.exp(0.65 * Math.log(scaleRadial) + 0.35 * Math.log(scaleAxial));
        double radialRobust = circle.robustSigma / Math.max(circle.radius, EPS);
        double scaledCoverage = Math.min(2.0, span * scale / lengthMm);
        double score = consistency * 2.2 + radialRobust * 1.6
                + Math.abs(1.0 - Math.min(1.0, scaledCoverage)) * 0.5;
        double[] axisOrigin = add(mean, add(scale(radialU, circle.cx), scale(radialV, circle.cy)));
        return new Candidate(axis, radialU, radialV, axisOrigin,
                (p05 + p95) * 0.5, scale, consistency, score);
    }

    private static List<Sample> qualityFiltered(List<PointCloudExportCore.Point> input) {
        if (input == null) return Collections.emptyList();
        List<Double> errors = new ArrayList<Double>();
        for (PointCloudExportCore.Point point : input) {
            if (point != null && finite(point.x, point.y, point.z)
                    && Double.isFinite(point.errorPx) && point.errorPx >= 0.0) {
                errors.add(point.errorPx);
            }
        }
        double limit = Double.POSITIVE_INFINITY;
        if (!errors.isEmpty()) {
            double median = median(errors);
            List<Double> deviations = new ArrayList<Double>(errors.size());
            for (double value : errors) deviations.add(Math.abs(value - median));
            limit = Math.max(4.0, median + 4.5 * Math.max(0.15, median(deviations)));
        }
        List<Sample> output = new ArrayList<Sample>();
        for (PointCloudExportCore.Point point : input) {
            if (point == null || !finite(point.x, point.y, point.z)) continue;
            if (Double.isFinite(point.errorPx) && point.errorPx >= 0.0 && point.errorPx > limit) continue;
            if (Double.isFinite(point.parallaxDegrees) && point.parallaxDegrees >= 0.0
                    && point.parallaxDegrees < 0.08) continue;
            output.add(new Sample(new double[]{point.x, point.y, point.z},
                    point.errorPx, point.parallaxDegrees));
        }
        return output;
    }

    private static List<Sample> distanceTrim(List<Sample> input, double[] center,
                                             double quantile, double multiplier) {
        if (input.size() < 16) return input;
        double[] distances = new double[input.size()];
        for (int i = 0; i < input.size(); i++) distances[i] = norm(subtract(input.get(i).xyz, center));
        double limit = percentile(distances, quantile) * multiplier;
        List<Sample> output = new ArrayList<Sample>();
        for (int i = 0; i < input.size(); i++) if (distances[i] <= limit) output.add(input.get(i));
        return output;
    }

    private static Circle fitCircleRobust(double[] x, double[] y) {
        double[] weights = new double[x.length];
        Arrays.fill(weights, 1.0);
        Circle circle = null;
        for (int iteration = 0; iteration < 5; iteration++) {
            circle = fitCircleWeighted(x, y, weights);
            if (circle == null) return null;
            double[] residuals = new double[x.length];
            for (int i = 0; i < x.length; i++) {
                residuals[i] = Math.abs(Math.hypot(x[i] - circle.cx, y[i] - circle.cy) - circle.radius);
            }
            double sigma = 1.4826 * Math.max(EPS, percentile(residuals, 0.50));
            double cutoff = Math.max(EPS, 2.5 * sigma);
            for (int i = 0; i < weights.length; i++) {
                weights[i] = residuals[i] <= cutoff ? 1.0 : cutoff / residuals[i];
            }
            circle = new Circle(circle.cx, circle.cy, circle.radius, sigma);
        }
        return circle;
    }

    private static Circle fitCircleWeighted(double[] x, double[] y, double[] weights) {
        double[][] normal = new double[3][3];
        double[] rhs = new double[3];
        double sumWeight = 0.0;
        for (int i = 0; i < x.length; i++) {
            double w = weights[i];
            double[] row = {2.0 * x[i], 2.0 * y[i], 1.0};
            double b = x[i] * x[i] + y[i] * y[i];
            for (int r = 0; r < 3; r++) {
                rhs[r] += w * row[r] * b;
                for (int c = 0; c < 3; c++) normal[r][c] += w * row[r] * row[c];
            }
            sumWeight += w;
        }
        if (sumWeight < 3.0) return null;
        double[] solution = solve3(normal, rhs);
        if (solution == null) return null;
        double radiusSquared = solution[2] + solution[0] * solution[0] + solution[1] * solution[1];
        if (!(radiusSquared > EPS)) return null;
        return new Circle(solution[0], solution[1], Math.sqrt(radiusSquared), 0.0);
    }

    private static double[] solve3(double[][] a, double[] b) {
        double[][] m = new double[3][4];
        for (int r = 0; r < 3; r++) {
            System.arraycopy(a[r], 0, m[r], 0, 3);
            m[r][3] = b[r];
        }
        for (int p = 0; p < 3; p++) {
            int pivot = p;
            for (int r = p + 1; r < 3; r++) if (Math.abs(m[r][p]) > Math.abs(m[pivot][p])) pivot = r;
            if (Math.abs(m[pivot][p]) < EPS) return null;
            double[] swap = m[p]; m[p] = m[pivot]; m[pivot] = swap;
            double divisor = m[p][p];
            for (int c = p; c < 4; c++) m[p][c] /= divisor;
            for (int r = 0; r < 3; r++) {
                if (r == p) continue;
                double factor = m[r][p];
                for (int c = p; c < 4; c++) m[r][c] -= factor * m[p][c];
            }
        }
        return new double[]{m[0][3], m[1][3], m[2][3]};
    }

    private static Eigen jacobi(double[][] input) {
        double[][] a = new double[3][3];
        double[][] v = identity();
        for (int i = 0; i < 3; i++) System.arraycopy(input[i], 0, a[i], 0, 3);
        for (int iteration = 0; iteration < 40; iteration++) {
            int p = 0, q = 1;
            double max = Math.abs(a[0][1]);
            if (Math.abs(a[0][2]) > max) { p = 0; q = 2; max = Math.abs(a[0][2]); }
            if (Math.abs(a[1][2]) > max) { p = 1; q = 2; max = Math.abs(a[1][2]); }
            if (max < 1e-12) break;
            double phi = 0.5 * Math.atan2(2.0 * a[p][q], a[q][q] - a[p][p]);
            double c = Math.cos(phi), s = Math.sin(phi);
            for (int k = 0; k < 3; k++) {
                double apk = a[p][k], aqk = a[q][k];
                a[p][k] = c * apk - s * aqk;
                a[q][k] = s * apk + c * aqk;
            }
            for (int k = 0; k < 3; k++) {
                double akp = a[k][p], akq = a[k][q];
                a[k][p] = c * akp - s * akq;
                a[k][q] = s * akp + c * akq;
            }
            for (int k = 0; k < 3; k++) {
                double vkp = v[k][p], vkq = v[k][q];
                v[k][p] = c * vkp - s * vkq;
                v[k][q] = s * vkp + c * vkq;
            }
        }
        return new Eigen(new double[]{a[0][0], a[1][1], a[2][2]}, v);
    }

    private static double coverage(List<Double> axial, double length) {
        if (axial.size() < 2 || !(length > 0.0)) return 0.0;
        Collections.sort(axial);
        double span = axial.get((int) Math.floor((axial.size() - 1) * 0.95))
                - axial.get((int) Math.floor((axial.size() - 1) * 0.05));
        return clamp(span / length, 0.0, 1.5);
    }

    private static double[] coordinateMedian(List<Sample> samples) {
        List<Double> x = new ArrayList<Double>(), y = new ArrayList<Double>(), z = new ArrayList<Double>();
        for (Sample sample : samples) { x.add(sample.xyz[0]); y.add(sample.xyz[1]); z.add(sample.xyz[2]); }
        return new double[]{median(x), median(y), median(z)};
    }

    private static double[] mean(List<Sample> samples) {
        double[] out = new double[3];
        for (Sample sample : samples) for (int i = 0; i < 3; i++) out[i] += sample.xyz[i];
        for (int i = 0; i < 3; i++) out[i] /= samples.size();
        return out;
    }

    private static double[][] covariance(List<Sample> samples, double[] mean) {
        double[][] c = new double[3][3];
        for (Sample sample : samples) {
            double[] d = subtract(sample.xyz, mean);
            for (int r = 0; r < 3; r++) for (int col = 0; col < 3; col++) c[r][col] += d[r] * d[col];
        }
        double denom = Math.max(1.0, samples.size() - 1.0);
        for (int r = 0; r < 3; r++) for (int col = 0; col < 3; col++) c[r][col] /= denom;
        return c;
    }

    private static double percentile(double[] input, double q) {
        if (input.length == 0) return Double.NaN;
        double[] copy = input.clone();
        Arrays.sort(copy);
        double position = clamp(q, 0.0, 1.0) * (copy.length - 1);
        int lower = (int) Math.floor(position), upper = (int) Math.ceil(position);
        if (lower == upper) return copy[lower];
        double t = position - lower;
        return copy[lower] * (1.0 - t) + copy[upper] * t;
    }

    private static double median(List<Double> input) {
        if (input.isEmpty()) return Double.NaN;
        List<Double> copy = new ArrayList<Double>(input);
        Collections.sort(copy);
        int middle = copy.size() / 2;
        return (copy.size() & 1) == 1 ? copy.get(middle)
                : 0.5 * (copy.get(middle - 1) + copy.get(middle));
    }

    private static double[] perpendicular(double[] axis) {
        double[] reference = Math.abs(axis[0]) < 0.75 ? new double[]{1,0,0}
                : Math.abs(axis[1]) < 0.75 ? new double[]{0,1,0} : new double[]{0,0,1};
        return normalize(cross(axis, reference));
    }

    private static double[] canonicalAxis(double[] axis) {
        double[] out = normalize(axis);
        int dominant = Math.abs(out[0]) >= Math.abs(out[1]) && Math.abs(out[0]) >= Math.abs(out[2]) ? 0
                : Math.abs(out[1]) >= Math.abs(out[2]) ? 1 : 2;
        if (out[dominant] < 0.0) for (int i = 0; i < 3; i++) out[i] = -out[i];
        return out;
    }

    private static double[][] identity() { return new double[][]{{1,0,0},{0,1,0},{0,0,1}}; }
    private static double[] add(double[] a, double[] b) { return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]}; }
    private static double[] subtract(double[] a, double[] b) { return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]}; }
    private static double[] scale(double[] a, double s) { return new double[]{a[0]*s,a[1]*s,a[2]*s}; }
    private static double dot(double[] a, double[] b) { return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
    private static double[] cross(double[] a, double[] b) { return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]}; }
    private static double norm(double[] a) { return Math.sqrt(dot(a,a)); }
    private static double[] normalize(double[] a) { double n=norm(a); return n>EPS?scale(a,1.0/n):new double[]{1,0,0}; }
    private static boolean finite(double... values) { for(double value:values) if(!Double.isFinite(value)) return false; return true; }
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}

    private static final class Sample {
        final double[] xyz; final double errorPx, parallaxDegrees;
        Sample(double[] xyz,double errorPx,double parallaxDegrees){this.xyz=xyz;this.errorPx=errorPx;this.parallaxDegrees=parallaxDegrees;}
    }
    private static final class Candidate {
        final double[] axis,radialU,radialV,axisOrigin; final double axialMidRaw,scaleMmPerRaw,scaleConsistency,score;
        Candidate(double[] axis,double[] radialU,double[] radialV,double[] axisOrigin,double axialMidRaw,double scaleMmPerRaw,double scaleConsistency,double score){this.axis=axis;this.radialU=radialU;this.radialV=radialV;this.axisOrigin=axisOrigin;this.axialMidRaw=axialMidRaw;this.scaleMmPerRaw=scaleMmPerRaw;this.scaleConsistency=scaleConsistency;this.score=score;}
    }
    private static final class Circle {
        final double cx,cy,radius,robustSigma;
        Circle(double cx,double cy,double radius,double robustSigma){this.cx=cx;this.cy=cy;this.radius=radius;this.robustSigma=robustSigma;}
    }
    private static final class Eigen {
        final double[] values; final double[][] vectors;
        Eigen(double[] values,double[][] vectors){this.values=values;this.vectors=vectors;}
    }

    public static final class AlignedPoint {
        public final double xMm,yMm,zMm;
        public final boolean inlier;
        public final double radialResidualMm,errorPx,parallaxDegrees;
        AlignedPoint(double xMm,double yMm,double zMm,boolean inlier,double radialResidualMm,double errorPx,double parallaxDegrees){this.xMm=xMm;this.yMm=yMm;this.zMm=zMm;this.inlier=inlier;this.radialResidualMm=radialResidualMm;this.errorPx=errorPx;this.parallaxDegrees=parallaxDegrees;}
    }

    public static final class Result {
        public final boolean solved,fitAccepted;
        public final String status;
        public final double shellLengthMm,shellDiameterMm;
        public final int rawPointCount,usedPointCount,inlierCount;
        public final double inlierRatio,radialRmsMm,axialCoverage,scaleMmPerRaw,scaleConsistency;
        public final double[] sourceAxis,sourceAxisOrigin;
        public final List<AlignedPoint> points;
        Result(boolean solved,boolean fitAccepted,String status,double shellLengthMm,double shellDiameterMm,int rawPointCount,int usedPointCount,int inlierCount,double inlierRatio,double radialRmsMm,double axialCoverage,double scaleMmPerRaw,double scaleConsistency,double[] sourceAxis,double[] sourceAxisOrigin,List<AlignedPoint> points){this.solved=solved;this.fitAccepted=fitAccepted;this.status=status;this.shellLengthMm=shellLengthMm;this.shellDiameterMm=shellDiameterMm;this.rawPointCount=rawPointCount;this.usedPointCount=usedPointCount;this.inlierCount=inlierCount;this.inlierRatio=inlierRatio;this.radialRmsMm=radialRmsMm;this.axialCoverage=axialCoverage;this.scaleMmPerRaw=scaleMmPerRaw;this.scaleConsistency=scaleConsistency;this.sourceAxis=sourceAxis==null?new double[]{1,0,0}:sourceAxis.clone();this.sourceAxisOrigin=sourceAxisOrigin==null?new double[]{0,0,0}:sourceAxisOrigin.clone();this.points=Collections.unmodifiableList(points==null?new ArrayList<AlignedPoint>():new ArrayList<AlignedPoint>(points));}
        static Result blocked(String status,double length,double diameter){return new Result(false,false,status,length,diameter,0,0,0,0.0,Double.POSITIVE_INFINITY,0.0,0.0,Double.POSITIVE_INFINITY,null,null,Collections.<AlignedPoint>emptyList());}
        public String summary(){return "MODELO CILÍNDRICO " + status + " · prior " + String.format(Locale.ROOT,"Ø%.1f × %.1f mm",shellDiameterMm,shellLengthMm) + " · inliers " + inlierCount + "/" + usedPointCount + " (" + String.format(Locale.ROOT,"%.0f%%",inlierRatio*100.0) + ") · RMS radial " + (Double.isFinite(radialRmsMm)?String.format(Locale.ROOT,"%.1f mm",radialRmsMm):"n/d") + " · cobertura axial " + String.format(Locale.ROOT,"%.0f%%",axialCoverage*100.0) + " · escala por dimensión conocida, no validación metrológica";}
    }
}
