package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded local bundle adjustment for Android: fixed rotations/intrinsics, camera 0 gauge,
 * robust point/translation block updates and translation priors that preserve local scale.
 * This is deliberately not a global bundle adjustment.
 */
public final class LocalBundleAdjustmentCore {
    public static final int MAX_CAMERAS = 8;
    public static final int MAX_POINTS = 120;
    public static final int MAX_OBSERVATIONS = 1500;

    private LocalBundleAdjustmentCore() {}

    public static Result optimize(Problem problem,
                                  PhotogrammetrySafetyGateCore.Result admission,
                                  int requestedIterations,
                                  double huberPx,
                                  double translationPriorWeight) {
        if (admission == null || admission.state != PhotogrammetrySafetyGateCore.State.READY) {
            return Result.failed("SAFETY_GATE_NOT_READY");
        }
        String validation = validate(problem);
        if (validation != null) return Result.failed(validation);
        int iterations = Math.max(2, Math.min(20, requestedIterations));
        double huber = Math.max(0.5, Math.min(8.0, huberPx));
        double priorWeight = Math.max(1e-6, Math.min(10.0, translationPriorWeight));

        List<Camera> cameras = copyCameras(problem.cameras);
        List<Point3> points = copyPoints(problem.points);
        List<double[]> translationPriors = new ArrayList<double[]>();
        for (Camera camera : cameras) translationPriors.add(camera.translation.clone());

        Stats initial = stats(cameras, points, problem.observations, huber);
        if (!Double.isFinite(initial.robustCost) || initial.validObservations < 12) {
            return Result.failed("INVALID_INITIAL_GEOMETRY");
        }
        double damping = 1e-3;
        double bestCost = initial.robustCost;
        List<Camera> bestCameras = copyCameras(cameras);
        List<Point3> bestPoints = copyPoints(points);
        int usedIterations = 0;
        int acceptedIterations = 0;

        for (int iteration = 0; iteration < iterations; iteration++) {
            usedIterations = iteration + 1;
            List<Camera> beforeCameras = copyCameras(cameras);
            List<Point3> beforePoints = copyPoints(points);
            refinePoints(cameras, points, problem.observations, huber, damping);
            refineTranslations(cameras, points, problem.observations, translationPriors,
                    huber, priorWeight, damping);
            Stats candidate = stats(cameras, points, problem.observations, huber);
            if (Double.isFinite(candidate.robustCost) && candidate.robustCost < bestCost) {
                double improvement = bestCost - candidate.robustCost;
                bestCost = candidate.robustCost;
                bestCameras = copyCameras(cameras);
                bestPoints = copyPoints(points);
                acceptedIterations++;
                damping = Math.max(1e-8, damping * 0.45);
                if (improvement < 1e-8) break;
            } else {
                cameras = beforeCameras;
                points = beforePoints;
                damping = Math.min(1e5, damping * 8.0);
            }
        }

        Stats finished = stats(bestCameras, bestPoints, problem.observations, huber);
        double improvementRatio = initial.rmsPx > 0.0 && Double.isFinite(initial.rmsPx)
                ? Math.max(0.0, (initial.rmsPx - finished.rmsPx) / initial.rmsPx) : 0.0;
        String status;
        if (!Double.isFinite(finished.rmsPx) || finished.positiveDepthRatio < 0.80) {
            status = "DIVERGED";
        } else if (finished.rmsPx <= initial.rmsPx * 0.70 && finished.rmsPx <= 2.0
                && acceptedIterations >= 1) {
            status = "CONVERGED";
        } else if (finished.rmsPx <= initial.rmsPx * 0.95 && acceptedIterations >= 1) {
            status = "IMPROVED";
        } else {
            status = "STALLED";
        }
        return new Result(true, status, bestCameras, bestPoints,
                initial.rmsPx, finished.rmsPx, initial.medianPx, finished.medianPx,
                finished.p90Px, improvementRatio, finished.positiveDepthRatio,
                initial.validObservations, usedIterations, acceptedIterations,
                bestCost, cameras.get(0).translation.clone());
    }

    private static String validate(Problem problem) {
        if (problem == null) return "NO_PROBLEM";
        if (problem.cameras.size() < 2) return "INSUFFICIENT_CAMERAS";
        if (problem.cameras.size() > MAX_CAMERAS) return "CAMERA_WINDOW_TOO_LARGE";
        if (problem.points.size() < 4) return "INSUFFICIENT_POINTS";
        if (problem.points.size() > MAX_POINTS) return "POINT_WINDOW_TOO_LARGE";
        if (problem.observations.size() < Math.max(12, problem.points.size()*2)) return "INSUFFICIENT_OBSERVATIONS";
        if (problem.observations.size() > MAX_OBSERVATIONS) return "OBSERVATION_WINDOW_TOO_LARGE";
        for (Camera camera : problem.cameras) if (camera == null || !camera.valid()) return "INVALID_CAMERA";
        for (Point3 point : problem.points) if (point == null || !point.valid()) return "INVALID_POINT";
        int[] perPoint = new int[problem.points.size()];
        for (Observation observation : problem.observations) {
            if (observation == null || !observation.valid(problem.cameras.size(), problem.points.size())) {
                return "INVALID_OBSERVATION";
            }
            perPoint[observation.pointIndex]++;
        }
        for (int count : perPoint) if (count < 2) return "POINT_WITH_INSUFFICIENT_VIEWS";
        return null;
    }

    private static void refinePoints(List<Camera> cameras, List<Point3> points,
                                     List<Observation> observations, double huber, double damping) {
        List<List<Observation>> byPoint = groupByPoint(points.size(), observations);
        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
            List<Observation> local = byPoint.get(pointIndex);
            if (local.size() < 2) continue;
            Point3 point = points.get(pointIndex);
            double[][] normal = new double[3][3];
            double[] rhs = new double[3];
            int valid = 0;
            for (Observation observation : local) {
                Projection projection = project(cameras.get(observation.cameraIndex), point);
                if (projection == null || projection.depth <= 1e-7) continue;
                valid++;
                double ru = observation.u - projection.u;
                double rv = observation.v - projection.v;
                double weight = observation.weight * huberWeight(Math.hypot(ru, rv), huber);
                accumulate(normal, rhs, projection.pointJu, ru, weight);
                accumulate(normal, rhs, projection.pointJv, rv, weight);
            }
            if (valid < 2) continue;
            for (int i=0;i<3;i++) normal[i][i] += damping;
            double[] delta = solve3(normal, rhs);
            if (delta == null || !finite(delta)) continue;
            Point3 candidate = new Point3(point.x+delta[0], point.y+delta[1], point.z+delta[2]);
            if (candidate.valid()) points.set(pointIndex, candidate);
        }
    }

    private static void refineTranslations(List<Camera> cameras, List<Point3> points,
                                           List<Observation> observations,
                                           List<double[]> priors, double huber,
                                           double priorWeight, double damping) {
        List<List<Observation>> byCamera = groupByCamera(cameras.size(), observations);
        for (int cameraIndex = 1; cameraIndex < cameras.size(); cameraIndex++) {
            Camera camera = cameras.get(cameraIndex);
            List<Observation> local = byCamera.get(cameraIndex);
            if (local.size() < 6) continue;
            double[][] normal = new double[3][3];
            double[] rhs = new double[3];
            int valid = 0;
            for (Observation observation : local) {
                Projection projection = project(camera, points.get(observation.pointIndex));
                if (projection == null || projection.depth <= 1e-7) continue;
                valid++;
                double ru = observation.u - projection.u;
                double rv = observation.v - projection.v;
                double weight = observation.weight * huberWeight(Math.hypot(ru, rv), huber);
                accumulate(normal, rhs, projection.translationJu, ru, weight);
                accumulate(normal, rhs, projection.translationJv, rv, weight);
            }
            if (valid < 6) continue;
            double[] prior = priors.get(cameraIndex);
            for (int axis=0;axis<3;axis++) {
                normal[axis][axis] += damping + priorWeight;
                rhs[axis] += priorWeight * (prior[axis] - camera.translation[axis]);
            }
            double[] delta = solve3(normal, rhs);
            if (delta == null || !finite(delta)) continue;
            double[] translation = new double[]{camera.translation[0]+delta[0],
                    camera.translation[1]+delta[1],camera.translation[2]+delta[2]};
            cameras.set(cameraIndex, new Camera(camera.rotation, translation,
                    camera.fx, camera.fy, camera.cx, camera.cy));
        }
    }

    private static Stats stats(List<Camera> cameras, List<Point3> points,
                               List<Observation> observations, double huber) {
        List<Double> errors = new ArrayList<Double>();
        double robust = 0.0;
        int positive = 0;
        for (Observation observation : observations) {
            Projection projection = project(cameras.get(observation.cameraIndex),
                    points.get(observation.pointIndex));
            if (projection == null) continue;
            if (projection.depth > 1e-7) positive++;
            double error = Math.hypot(projection.u-observation.u, projection.v-observation.v);
            if (!Double.isFinite(error)) continue;
            errors.add(error);
            robust += observation.weight * huberLoss(error, huber);
        }
        if (errors.isEmpty()) return Stats.invalid();
        double median = percentile(errors,0.5);
        double threshold = Math.max(huber*3.0, median*3.5+0.5);
        double squared = 0.0;
        int inliers = 0;
        for (double error : errors) {
            if (error <= threshold) { squared += error*error; inliers++; }
        }
        double rms = inliers == 0 ? Double.POSITIVE_INFINITY : Math.sqrt(squared/inliers);
        return new Stats(rms, median, percentile(errors,0.9),
                robust/errors.size(), errors.size(), positive/(double) observations.size());
    }

    private static Projection project(Camera camera, Point3 point) {
        double x=camera.rotation[0][0]*point.x+camera.rotation[0][1]*point.y
                +camera.rotation[0][2]*point.z+camera.translation[0];
        double y=camera.rotation[1][0]*point.x+camera.rotation[1][1]*point.y
                +camera.rotation[1][2]*point.z+camera.translation[1];
        double z=camera.rotation[2][0]*point.x+camera.rotation[2][1]*point.y
                +camera.rotation[2][2]*point.z+camera.translation[2];
        if (!Double.isFinite(z) || Math.abs(z)<1e-10) return null;
        double u=camera.fx*x/z+camera.cx;
        double v=camera.fy*y/z+camera.cy;
        double[] translationJu=new double[]{camera.fx/z,0,-camera.fx*x/(z*z)};
        double[] translationJv=new double[]{0,camera.fy/z,-camera.fy*y/(z*z)};
        double[] pointJu=new double[3];
        double[] pointJv=new double[3];
        for(int column=0;column<3;column++) {
            pointJu[column]=camera.fx*(camera.rotation[0][column]*z-x*camera.rotation[2][column])/(z*z);
            pointJv[column]=camera.fy*(camera.rotation[1][column]*z-y*camera.rotation[2][column])/(z*z);
        }
        return new Projection(u,v,z,pointJu,pointJv,translationJu,translationJv);
    }

    private static List<List<Observation>> groupByPoint(int count,List<Observation> observations){
        List<List<Observation>> result=new ArrayList<List<Observation>>();for(int i=0;i<count;i++)result.add(new ArrayList<Observation>());
        for(Observation observation:observations)result.get(observation.pointIndex).add(observation);return result;
    }
    private static List<List<Observation>> groupByCamera(int count,List<Observation> observations){
        List<List<Observation>> result=new ArrayList<List<Observation>>();for(int i=0;i<count;i++)result.add(new ArrayList<Observation>());
        for(Observation observation:observations)result.get(observation.cameraIndex).add(observation);return result;
    }
    private static void accumulate(double[][] normal,double[] rhs,double[] jacobian,double residual,double weight){for(int i=0;i<3;i++){rhs[i]+=weight*jacobian[i]*residual;for(int j=0;j<3;j++)normal[i][j]+=weight*jacobian[i]*jacobian[j];}}
    private static double huberWeight(double error,double threshold){return error<=threshold?1.0:threshold/Math.max(error,1e-12);}
    private static double huberLoss(double error,double threshold){return error<=threshold?0.5*error*error:threshold*(error-0.5*threshold);}
    private static double[] solve3(double[][] a,double[] b){double[][]m=new double[3][4];for(int i=0;i<3;i++){System.arraycopy(a[i],0,m[i],0,3);m[i][3]=b[i];}for(int col=0;col<3;col++){int pivot=col;for(int row=col+1;row<3;row++)if(Math.abs(m[row][col])>Math.abs(m[pivot][col]))pivot=row;if(Math.abs(m[pivot][col])<1e-12)return null;double[]tmp=m[col];m[col]=m[pivot];m[pivot]=tmp;double d=m[col][col];for(int j=col;j<4;j++)m[col][j]/=d;for(int row=0;row<3;row++)if(row!=col){double f=m[row][col];for(int j=col;j<4;j++)m[row][j]-=f*m[col][j];}}return new double[]{m[0][3],m[1][3],m[2][3]};}
    private static double percentile(List<Double> source,double q){if(source.isEmpty())return Double.POSITIVE_INFINITY;List<Double>v=new ArrayList<Double>(source);Collections.sort(v);double p=Math.max(0,Math.min(1,q))*(v.size()-1);int lo=(int)Math.floor(p),hi=(int)Math.ceil(p);return lo==hi?v.get(lo):v.get(lo)*(hi-p)+v.get(hi)*(p-lo);}
    private static boolean finite(double[]v){for(double x:v)if(!Double.isFinite(x))return false;return true;}
    private static List<Camera> copyCameras(List<Camera>source){List<Camera>r=new ArrayList<Camera>();for(Camera c:source)r.add(new Camera(c.rotation,c.translation,c.fx,c.fy,c.cx,c.cy));return r;}
    private static List<Point3> copyPoints(List<Point3>source){List<Point3>r=new ArrayList<Point3>();for(Point3 p:source)r.add(new Point3(p.x,p.y,p.z));return r;}
    private static double[][] copy(double[][]source){if(source==null)return null;double[][]r=new double[source.length][];for(int i=0;i<source.length;i++)r[i]=source[i].clone();return r;}

    public static final class Camera {
        public final double[][] rotation; public final double[] translation; public final double fx,fy,cx,cy;
        public Camera(double[][]rotation,double[]translation,double fx,double fy,double cx,double cy){this.rotation=copy(rotation);this.translation=translation==null?null:translation.clone();this.fx=fx;this.fy=fy;this.cx=cx;this.cy=cy;}
        boolean valid(){return rotation!=null&&rotation.length==3&&rotation[0].length==3&&rotation[1].length==3&&rotation[2].length==3&&translation!=null&&translation.length==3&&finite(translation)&&Double.isFinite(fx)&&Double.isFinite(fy)&&fx>0&&fy>0&&Double.isFinite(cx)&&Double.isFinite(cy);}
    }
    public static final class Point3 {
        public final double x,y,z; public Point3(double x,double y,double z){this.x=x;this.y=y;this.z=z;} boolean valid(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    }
    public static final class Observation {
        public final int cameraIndex,pointIndex;public final double u,v,weight;
        public Observation(int cameraIndex,int pointIndex,double u,double v,double weight){this.cameraIndex=cameraIndex;this.pointIndex=pointIndex;this.u=u;this.v=v;this.weight=weight;}
        boolean valid(int cameras,int points){return cameraIndex>=0&&cameraIndex<cameras&&pointIndex>=0&&pointIndex<points&&Double.isFinite(u)&&Double.isFinite(v)&&Double.isFinite(weight)&&weight>0;}
    }
    public static final class Problem {
        public final List<Camera> cameras;public final List<Point3> points;public final List<Observation> observations;
        public Problem(List<Camera>cameras,List<Point3>points,List<Observation>observations){this.cameras=Collections.unmodifiableList(copyCameras(cameras==null?Collections.<Camera>emptyList():cameras));this.points=Collections.unmodifiableList(copyPoints(points==null?Collections.<Point3>emptyList():points));this.observations=Collections.unmodifiableList(new ArrayList<Observation>(observations==null?Collections.<Observation>emptyList():observations));}
    }
    public static final class Result {
        public final boolean solved;public final String status;public final List<Camera>cameras;public final List<Point3>points;public final double initialRmsPx,finalRmsPx,initialMedianPx,finalMedianPx,finalP90Px,improvementRatio,positiveDepthRatio;public final int observations,iterations,acceptedIterations;public final double robustCost;public final double[]fixedGaugeTranslation;
        Result(boolean solved,String status,List<Camera>cameras,List<Point3>points,double initialRmsPx,double finalRmsPx,double initialMedianPx,double finalMedianPx,double finalP90Px,double improvementRatio,double positiveDepthRatio,int observations,int iterations,int acceptedIterations,double robustCost,double[]fixedGaugeTranslation){this.solved=solved;this.status=status;this.cameras=Collections.unmodifiableList(copyCameras(cameras));this.points=Collections.unmodifiableList(copyPoints(points));this.initialRmsPx=initialRmsPx;this.finalRmsPx=finalRmsPx;this.initialMedianPx=initialMedianPx;this.finalMedianPx=finalMedianPx;this.finalP90Px=finalP90Px;this.improvementRatio=improvementRatio;this.positiveDepthRatio=positiveDepthRatio;this.observations=observations;this.iterations=iterations;this.acceptedIterations=acceptedIterations;this.robustCost=robustCost;this.fixedGaugeTranslation=fixedGaugeTranslation==null?null:fixedGaugeTranslation.clone();}
        static Result failed(String status){return new Result(false,status,Collections.<Camera>emptyList(),Collections.<Point3>emptyList(),Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,0,0,0,0,0,Double.POSITIVE_INFINITY,null);}
        public boolean ready(){return solved&&("CONVERGED".equals(status)||"IMPROVED".equals(status));}
    }
    private static final class Projection {final double u,v,depth;final double[]pointJu,pointJv,translationJu,translationJv;Projection(double u,double v,double depth,double[]pointJu,double[]pointJv,double[]translationJu,double[]translationJv){this.u=u;this.v=v;this.depth=depth;this.pointJu=pointJu;this.pointJv=pointJv;this.translationJu=translationJu;this.translationJv=translationJv;}}
    private static final class Stats {final double rmsPx,medianPx,p90Px,robustCost;final int validObservations;final double positiveDepthRatio;Stats(double rmsPx,double medianPx,double p90Px,double robustCost,int validObservations,double positiveDepthRatio){this.rmsPx=rmsPx;this.medianPx=medianPx;this.p90Px=p90Px;this.robustCost=robustCost;this.validObservations=validObservations;this.positiveDepthRatio=positiveDepthRatio;}static Stats invalid(){return new Stats(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,0,0);}}
}
