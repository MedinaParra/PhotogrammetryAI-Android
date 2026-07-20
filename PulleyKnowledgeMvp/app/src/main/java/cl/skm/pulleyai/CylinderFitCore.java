package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Robust axis/radius/length fit specialized for pulley shell point clouds. */
public final class CylinderFitCore {
    private CylinderFitCore() {}

    public static Result fit(List<Point> source) {
        if (source == null || source.size() < 30) return Result.failed("INSUFFICIENT_POINTS");
        List<Point> points = new ArrayList<Point>();
        for (Point point : source) if (point != null && point.finite()) points.add(point);
        if (points.size() < 30) return Result.failed("INSUFFICIENT_FINITE_POINTS");

        List<Point> inliers = new ArrayList<Point>(points);
        double[] center = null;
        double[] axis = null;
        double radius = Double.NaN;
        for (int iteration=0; iteration<4; iteration++) {
            center = centroid(inliers);
            axis = principalAxis(inliers, center);
            if (axis == null) return Result.failed("AXIS_FAILED");
            orient(axis);
            List<Double> radii = new ArrayList<Double>();
            for (Point point : inliers) radii.add(radialDistance(point,center,axis));
            radius = percentile(radii,0.5);
            List<Double> residuals = new ArrayList<Double>();
            for (double value : radii) residuals.add(Math.abs(value-radius));
            double mad = percentile(residuals,0.5);
            double threshold = Math.max(radius*0.035, Math.max(0.004, 3.5*1.4826*mad));
            List<Point> filtered = new ArrayList<Point>();
            for (int i=0;i<inliers.size();i++) {
                if (residuals.get(i)<=threshold) filtered.add(inliers.get(i));
            }
            if (filtered.size()<Math.max(30,points.size()/3)) break;
            if (filtered.size()==inliers.size()) break;
            inliers=filtered;
        }
        center=centroid(inliers);
        axis=principalAxis(inliers,center);
        if(axis==null)return Result.failed("AXIS_FAILED");
        orient(axis);
        List<Double> radii=new ArrayList<Double>();
        List<Double> axial=new ArrayList<Double>();
        double residualSum=0;
        for(Point point:inliers){
            double r=radialDistance(point,center,axis);
            radii.add(r);
            axial.add(project(point,center,axis));
        }
        radius=percentile(radii,0.5);
        for(double r:radii){double d=r-radius;residualSum+=d*d;}
        double radialRms=Math.sqrt(residualSum/inliers.size());
        double low=percentile(axial,0.02), high=percentile(axial,0.98);
        double length=Math.max(0.0,high-low);
        double[] start=new double[]{center[0]+axis[0]*low,center[1]+axis[1]*low,center[2]+axis[2]*low};
        double[] end=new double[]{center[0]+axis[0]*high,center[1]+axis[1]*high,center[2]+axis[2]*high};
        double inlierRatio=(double)inliers.size()/points.size();
        String status=inliers.size()>=180 && inlierRatio>=0.70
                && radialRms<=Math.max(0.008,radius*0.025)
                && length>=radius*0.7 ? "STRONG"
                : inliers.size()>=70 && inlierRatio>=0.50
                && radialRms<=Math.max(0.015,radius*0.06)
                && length>=radius*0.4 ? "USABLE" : "WEAK";
        return new Result(true,status,center,axis,start,end,radius,length,
                radialRms,inliers.size(),points.size(),inlierRatio);
    }

    private static double[] centroid(List<Point> points){
        double x=0,y=0,z=0;
        for(Point p:points){x+=p.x;y+=p.y;z+=p.z;}
        double n=points.size();
        return new double[]{x/n,y/n,z/n};
    }

    private static double[] principalAxis(List<Point> points,double[] center){
        double[][] c=new double[3][3];
        for(Point p:points){
            double x=p.x-center[0],y=p.y-center[1],z=p.z-center[2];
            c[0][0]+=x*x;c[0][1]+=x*y;c[0][2]+=x*z;
            c[1][0]+=x*y;c[1][1]+=y*y;c[1][2]+=y*z;
            c[2][0]+=x*z;c[2][1]+=y*z;c[2][2]+=z*z;
        }
        double[] v=new double[]{1,0.3,0.2};
        normalize(v);
        for(int iteration=0;iteration<80;iteration++){
            double[] next=new double[]{
                    c[0][0]*v[0]+c[0][1]*v[1]+c[0][2]*v[2],
                    c[1][0]*v[0]+c[1][1]*v[1]+c[1][2]*v[2],
                    c[2][0]*v[0]+c[2][1]*v[1]+c[2][2]*v[2]};
            double norm=norm(next);
            if(norm<1e-12)return null;
            for(int i=0;i<3;i++)next[i]/=norm;
            double change=Math.abs(next[0]-v[0])+Math.abs(next[1]-v[1])+Math.abs(next[2]-v[2]);
            v=next;
            if(change<1e-10)break;
        }
        return v;
    }

    private static double radialDistance(Point p,double[] center,double[] axis){
        double dx=p.x-center[0],dy=p.y-center[1],dz=p.z-center[2];
        double projection=dx*axis[0]+dy*axis[1]+dz*axis[2];
        double rx=dx-projection*axis[0],ry=dy-projection*axis[1],rz=dz-projection*axis[2];
        return Math.sqrt(rx*rx+ry*ry+rz*rz);
    }
    private static double project(Point p,double[] center,double[] axis){
        return (p.x-center[0])*axis[0]+(p.y-center[1])*axis[1]+(p.z-center[2])*axis[2];
    }
    private static double percentile(List<Double> source,double q){
        if(source.isEmpty())return 0.0;
        List<Double> values=new ArrayList<Double>(source);
        Collections.sort(values);
        double p=Math.max(0,Math.min(1,q))*(values.size()-1);
        int lo=(int)Math.floor(p),hi=(int)Math.ceil(p);
        if(lo==hi)return values.get(lo);
        return values.get(lo)*(hi-p)+values.get(hi)*(p-lo);
    }
    private static void orient(double[] axis){
        int index=0;
        if(Math.abs(axis[1])>Math.abs(axis[index]))index=1;
        if(Math.abs(axis[2])>Math.abs(axis[index]))index=2;
        if(axis[index]<0)for(int i=0;i<3;i++)axis[i]=-axis[i];
    }
    private static void normalize(double[] v){double n=norm(v);if(n>1e-12)for(int i=0;i<3;i++)v[i]/=n;}
    private static double norm(double[] v){return Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);}

    public static final class Point {
        public final double x,y,z;
        public Point(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    }

    public static final class Result {
        public final boolean solved;
        public final String status;
        public final double[] center;
        public final double[] axis;
        public final double[] start;
        public final double[] end;
        public final double radius;
        public final double length;
        public final double radialRms;
        public final int inlierCount;
        public final int totalCount;
        public final double inlierRatio;
        Result(boolean solved,String status,double[] center,double[] axis,double[] start,double[] end,
               double radius,double length,double radialRms,int inlierCount,int totalCount,double inlierRatio){
            this.solved=solved;this.status=status;
            this.center=center==null?null:center.clone();this.axis=axis==null?null:axis.clone();
            this.start=start==null?null:start.clone();this.end=end==null?null:end.clone();
            this.radius=radius;this.length=length;this.radialRms=radialRms;
            this.inlierCount=inlierCount;this.totalCount=totalCount;this.inlierRatio=inlierRatio;
        }
        static Result failed(String status){
            return new Result(false,status,null,null,null,null,Double.NaN,Double.NaN,
                    Double.POSITIVE_INFINITY,0,0,0.0);
        }
        public boolean ready(){return "STRONG".equals(status)||"USABLE".equals(status);}
        public String summary(){
            return "Ø "+format(radius*2.0)+" · L "+format(length)+" · RMS "
                    +format(radialRms)+" · inliers "+inlierCount+"/"+totalCount+" · "+status;
        }
        private static String format(double value){
            return String.format(java.util.Locale.ROOT,"%.4f",value);
        }
    }
}
