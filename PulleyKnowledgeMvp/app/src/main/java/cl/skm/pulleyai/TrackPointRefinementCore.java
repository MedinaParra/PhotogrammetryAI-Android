package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Refines a 3D track point against three or more calibrated camera observations. */
public final class TrackPointRefinementCore {
    private TrackPointRefinementCore() {}

    public static Result refine(double[] initial, List<Observation> source,
                                int iterations, double huberPx) {
        if (initial == null || initial.length != 3 || !finite(initial)
                || source == null || source.size() < 3) {
            return Result.failed("INSUFFICIENT_OBSERVATIONS");
        }
        List<Observation> observations = new ArrayList<Observation>();
        for (Observation observation : source) {
            if (observation != null && observation.valid()) observations.add(observation);
        }
        if (observations.size() < 3) return Result.failed("INVALID_OBSERVATIONS");
        double[] point = initial.clone();
        double damping = 1e-4;
        double previousCost = cost(point, observations, huberPx);
        if (!Double.isFinite(previousCost)) return Result.failed("INVALID_INITIAL_POINT");
        int usedIterations = 0;
        for (int iteration=0;iteration<Math.max(3,Math.min(40,iterations));iteration++) {
            usedIterations = iteration + 1;
            double[][] normal = new double[3][3];
            double[] rhs = new double[3];
            int valid = 0;
            for (Observation observation : observations) {
                Projection projection = project(point, observation.camera);
                if (projection == null || projection.depth <= 1e-7) continue;
                valid++;
                double ru = observation.u - projection.u;
                double rv = observation.v - projection.v;
                double error = Math.sqrt(ru*ru + rv*rv);
                double weight = huberWeight(error, huberPx);
                accumulate(normal, rhs, projection.ju, ru, weight);
                accumulate(normal, rhs, projection.jv, rv, weight);
            }
            if (valid < 3) return Result.failed("POINT_BEHIND_CAMERAS");
            for (int i=0;i<3;i++) normal[i][i] += damping;
            double[] delta = solve3(normal, rhs);
            if (delta == null || !finite(delta)) return Result.failed("NORMAL_EQUATION_FAILED");
            double[] candidate = new double[]{point[0]+delta[0],point[1]+delta[1],point[2]+delta[2]};
            double candidateCost = cost(candidate, observations, huberPx);
            if (candidateCost < previousCost) {
                point = candidate;
                double improvement = previousCost-candidateCost;
                previousCost = candidateCost;
                damping = Math.max(1e-8,damping*0.35);
                if (norm(delta)<1e-7 || improvement<1e-8) break;
            } else {
                damping = Math.min(1e6,damping*8.0);
            }
        }
        List<Double> errors = new ArrayList<Double>();
        int positive = 0;
        for (Observation observation : observations) {
            Projection projection = project(point, observation.camera);
            if (projection == null) continue;
            if (projection.depth > 1e-7) positive++;
            double du = projection.u-observation.u;
            double dv = projection.v-observation.v;
            errors.add(Math.sqrt(du*du+dv*dv));
        }
        double rms = rms(errors);
        double median = percentile(errors,0.5);
        double p90 = percentile(errors,0.9);
        double positiveRatio = (double)positive/observations.size();
        String status = observations.size()>=5 && positiveRatio>=0.95
                && rms<=1.2 && p90<=2.0 ? "STRONG"
                : observations.size()>=3 && positiveRatio>=0.80
                && rms<=2.5 && p90<=4.0 ? "USABLE" : "WEAK";
        return new Result(true,status,point,observations.size(),positiveRatio,
                rms,median,p90,usedIterations,previousCost);
    }

    private static Projection project(double[] point, Camera camera) {
        double x=camera.rotation[0][0]*point[0]+camera.rotation[0][1]*point[1]
                +camera.rotation[0][2]*point[2]+camera.translation[0];
        double y=camera.rotation[1][0]*point[0]+camera.rotation[1][1]*point[1]
                +camera.rotation[1][2]*point[2]+camera.translation[1];
        double z=camera.rotation[2][0]*point[0]+camera.rotation[2][1]*point[1]
                +camera.rotation[2][2]*point[2]+camera.translation[2];
        if (!Double.isFinite(z) || Math.abs(z)<1e-10) return null;
        double u=camera.fx*x/z+camera.cx;
        double v=camera.fy*y/z+camera.cy;
        double[] ju=new double[3];
        double[] jv=new double[3];
        for(int column=0;column<3;column++) {
            ju[column]=camera.fx*(camera.rotation[0][column]*z-x*camera.rotation[2][column])/(z*z);
            jv[column]=camera.fy*(camera.rotation[1][column]*z-y*camera.rotation[2][column])/(z*z);
        }
        return new Projection(u,v,z,ju,jv);
    }

    private static double cost(double[] point,List<Observation> observations,double huber) {
        double total=0;int count=0;
        for(Observation observation:observations) {
            Projection projection=project(point,observation.camera);
            if(projection==null || projection.depth<=1e-7)continue;
            double du=projection.u-observation.u,dv=projection.v-observation.v;
            double e=Math.sqrt(du*du+dv*dv);
            total+=huberLoss(e,huber);count++;
        }
        return count<3?Double.POSITIVE_INFINITY:total/count;
    }

    private static void accumulate(double[][] normal,double[] rhs,double[] jacobian,
                                   double residual,double weight) {
        for(int i=0;i<3;i++) {
            rhs[i]+=weight*jacobian[i]*residual;
            for(int j=0;j<3;j++)normal[i][j]+=weight*jacobian[i]*jacobian[j];
        }
    }
    private static double huberWeight(double error,double threshold) {
        double t=Math.max(0.5,threshold);
        return error<=t?1.0:t/Math.max(error,1e-12);
    }
    private static double huberLoss(double error,double threshold) {
        double t=Math.max(0.5,threshold);
        return error<=t?0.5*error*error:t*(error-0.5*t);
    }
    private static double[] solve3(double[][] a,double[] b) {
        double[][] m=new double[3][4];
        for(int i=0;i<3;i++){System.arraycopy(a[i],0,m[i],0,3);m[i][3]=b[i];}
        for(int col=0;col<3;col++) {
            int pivot=col;
            for(int row=col+1;row<3;row++)if(Math.abs(m[row][col])>Math.abs(m[pivot][col]))pivot=row;
            if(Math.abs(m[pivot][col])<1e-12)return null;
            double[] temp=m[col];m[col]=m[pivot];m[pivot]=temp;
            double divisor=m[col][col];for(int j=col;j<4;j++)m[col][j]/=divisor;
            for(int row=0;row<3;row++)if(row!=col){double f=m[row][col];for(int j=col;j<4;j++)m[row][j]-=f*m[col][j];}
        }
        return new double[]{m[0][3],m[1][3],m[2][3]};
    }
    private static double rms(List<Double> errors){double sum=0;for(double e:errors)sum+=e*e;return errors.isEmpty()?Double.POSITIVE_INFINITY:Math.sqrt(sum/errors.size());}
    private static double percentile(List<Double> source,double q){if(source.isEmpty())return Double.POSITIVE_INFINITY;List<Double>values=new ArrayList<Double>(source);Collections.sort(values);double p=Math.max(0,Math.min(1,q))*(values.size()-1);int lo=(int)Math.floor(p),hi=(int)Math.ceil(p);return lo==hi?values.get(lo):values.get(lo)*(hi-p)+values.get(hi)*(p-lo);}
    private static boolean finite(double[] values){for(double value:values)if(!Double.isFinite(value))return false;return true;}
    private static double norm(double[] values){return Math.sqrt(values[0]*values[0]+values[1]*values[1]+values[2]*values[2]);}

    public static final class Camera {
        public final double[][] rotation;
        public final double[] translation;
        public final double fx,fy,cx,cy;
        public Camera(double[][] rotation,double[] translation,double fx,double fy,double cx,double cy) {
            this.rotation=copy(rotation);this.translation=translation==null?null:translation.clone();
            this.fx=fx;this.fy=fy;this.cx=cx;this.cy=cy;
        }
        boolean valid(){return rotation!=null&&rotation.length==3&&rotation[0].length==3
                &&translation!=null&&translation.length==3&&finite(translation)
                &&Double.isFinite(fx)&&Double.isFinite(fy)&&fx>0&&fy>0;}
    }
    public static final class Observation {
        public final Camera camera;
        public final double u,v;
        public Observation(Camera camera,double u,double v){this.camera=camera;this.u=u;this.v=v;}
        boolean valid(){return camera!=null&&camera.valid()&&Double.isFinite(u)&&Double.isFinite(v);}
    }
    public static final class Result {
        public final boolean solved;
        public final String status;
        public final double[] point;
        public final int observations;
        public final double positiveDepthRatio;
        public final double rmsPx,medianPx,p90Px;
        public final int iterations;
        public final double robustCost;
        Result(boolean solved,String status,double[] point,int observations,double positiveDepthRatio,
               double rmsPx,double medianPx,double p90Px,int iterations,double robustCost) {
            this.solved=solved;this.status=status;this.point=point==null?null:point.clone();
            this.observations=observations;this.positiveDepthRatio=positiveDepthRatio;
            this.rmsPx=rmsPx;this.medianPx=medianPx;this.p90Px=p90Px;
            this.iterations=iterations;this.robustCost=robustCost;
        }
        static Result failed(String status){return new Result(false,status,null,0,0,
                Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,0,Double.POSITIVE_INFINITY);}
        public boolean ready(){return "STRONG".equals(status)||"USABLE".equals(status);}
        public String summary(){return "Obs "+observations+" · RMS "+format(rmsPx)+" px · P90 "+format(p90Px)+" px · "+status;}
        private static String format(double value){return String.format(java.util.Locale.ROOT,"%.3f",value);}
    }
    private static final class Projection {
        final double u,v,depth;final double[]ju,jv;
        Projection(double u,double v,double depth,double[]ju,double[]jv){this.u=u;this.v=v;this.depth=depth;this.ju=ju;this.jv=jv;}
    }
    private static double[][] copy(double[][] source){if(source==null)return null;double[][]r=new double[source.length][];for(int i=0;i<source.length;i++)r[i]=source[i].clone();return r;}
}
