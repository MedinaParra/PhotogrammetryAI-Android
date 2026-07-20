package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministic RANSAC cylinder segmentation for separating a pulley shell from background points. */
public final class PulleyShellRansacCore {
    private PulleyShellRansacCore() {}

    public static Result fit(List<Point> source, int iterations) {
        if (source == null || source.size() < 40) return Result.failed("INSUFFICIENT_POINTS");
        List<Point> points = new ArrayList<Point>();
        for (Point point : source) if (point != null && point.finite()) points.add(point);
        if (points.size() < 40) return Result.failed("INSUFFICIENT_FINITE_POINTS");
        double[] origin = coordinateMedian(points);
        Eigen eigen = covarianceEigen(trimmed(points, origin, 0.82), origin);
        if (eigen == null) return Result.failed("PCA_FAILED");
        double diagonal = boundingDiagonal(points);
        List<double[]> axes = new ArrayList<double[]>();
        for (int axisIndex=0;axisIndex<3;axisIndex++) {
            double[] axis=column(eigen.vectors,axisIndex); normalize(axis); orient(axis); axes.add(axis);
        }
        axes.addAll(normalAxisCandidates(points, Math.max(80, Math.min(260, iterations/3))));
        Candidate best = null;
        long state = 0x9e3779b97f4a7c15L ^ points.size();
        int circleTrials = Math.max(28, Math.min(90, iterations/12));
        for (double[] axis : axes) {
            Basis basis = basis(axis);
            List<Projected> projected = project(points, origin, axis, basis);
            for (int trial=0;trial<circleTrials;trial++) {
                state = next(state); int i=(int)Math.floorMod(state, projected.size());
                state = next(state); int j=(int)Math.floorMod(state, projected.size());
                state = next(state); int k=(int)Math.floorMod(state, projected.size());
                if (i==j || i==k || j==k) continue;
                Circle circle = circle(projected.get(i),projected.get(j),projected.get(k));
                if (circle == null || circle.radius < diagonal*0.025 || circle.radius > diagonal*1.2) continue;
                Candidate candidate = evaluate(projected, axis, basis, origin, circle, diagonal);
                if (best == null || candidate.score > best.score) best = candidate;
            }
        }
        if (best == null || best.inlierIndices.size() < 30) return Result.failed("CYLINDER_NOT_FOUND");
        Candidate refined = refine(points, best, diagonal);
        double ratio = (double) refined.inlierIndices.size()/points.size();
        String status = refined.inlierIndices.size() >= 180 && ratio >= 0.45
                && refined.radialRms <= Math.max(diagonal*0.004, refined.radius*0.025)
                && refined.length >= refined.radius*0.6 ? "STRONG"
                : refined.inlierIndices.size() >= 70 && ratio >= 0.25
                && refined.radialRms <= Math.max(diagonal*0.009, refined.radius*0.06)
                && refined.length >= refined.radius*0.35 ? "USABLE" : "WEAK";
        return new Result(true,status,refined.axis,refined.centerLine,
                refined.radius,refined.length,refined.radialRms,
                refined.inlierIndices,points.size(),ratio);
    }

    private static List<double[]> normalAxisCandidates(List<Point> points, int requested) {
        List<double[]> normals = new ArrayList<double[]>();
        int step=Math.max(1,points.size()/Math.max(60,Math.min(180,requested)));
        for(int index=0;index<points.size();index+=step) {
            double[] normal=localNormal(points,index,14);
            if(normal!=null)normals.add(normal);
        }
        List<double[]> axes=new ArrayList<double[]>();
        long state=0x1234abcd55aa7711L^points.size();
        int trials=Math.max(80,requested);
        for(int i=0;i<trials && normals.size()>=2;i++) {
            state=next(state);double[] a=normals.get((int)Math.floorMod(state,normals.size()));
            state=next(state);double[] b=normals.get((int)Math.floorMod(state,normals.size()));
            double[] axis=cross(a,b);
            double n=Math.sqrt(axis[0]*axis[0]+axis[1]*axis[1]+axis[2]*axis[2]);
            if(n<0.22)continue;
            axis[0]/=n;axis[1]/=n;axis[2]/=n;orient(axis);
            boolean duplicate=false;
            for(double[] existing:axes) {
                double dot=Math.abs(axis[0]*existing[0]+axis[1]*existing[1]+axis[2]*existing[2]);
                if(dot>0.995){duplicate=true;break;}
            }
            if(!duplicate)axes.add(axis);
        }
        return axes;
    }

    private static double[] localNormal(List<Point> points,int target,int neighbors) {
        Point center=points.get(target);
        int k=Math.min(neighbors,points.size()-1);
        double[] distances=new double[k];
        int[] indices=new int[k];
        java.util.Arrays.fill(distances,Double.POSITIVE_INFINITY);
        for(int i=0;i<points.size();i++) {
            if(i==target)continue;
            Point p=points.get(i);
            double dx=p.x-center.x,dy=p.y-center.y,dz=p.z-center.z;
            double d=dx*dx+dy*dy+dz*dz;
            int worst=0;
            for(int j=1;j<k;j++)if(distances[j]>distances[worst])worst=j;
            if(d<distances[worst]){distances[worst]=d;indices[worst]=i;}
        }
        List<Point> neighborhood=new ArrayList<Point>();
        neighborhood.add(center);
        for(int i=0;i<k;i++)if(Double.isFinite(distances[i]))neighborhood.add(points.get(indices[i]));
        if(neighborhood.size()<6)return null;
        double[] centroid=centroid(neighborhood);
        Eigen eigen=covarianceEigen(neighborhood,centroid);
        if(eigen==null)return null;
        int smallest=0;
        for(int i=1;i<3;i++)if(eigen.values[i]<eigen.values[smallest])smallest=i;
        double[] normal=column(eigen.vectors,smallest);normalize(normal);
        return normal;
    }

    private static List<Point> trimmed(List<Point> points,double[] center,double fraction) {
        List<DistancePoint> values=new ArrayList<DistancePoint>();
        for(Point p:points){double dx=p.x-center[0],dy=p.y-center[1],dz=p.z-center[2];values.add(new DistancePoint(p,dx*dx+dy*dy+dz*dz));}
        Collections.sort(values,new java.util.Comparator<DistancePoint>(){@Override public int compare(DistancePoint a,DistancePoint b){return Double.compare(a.distance,b.distance);}});
        int limit=Math.max(30,(int)Math.round(values.size()*fraction));
        List<Point> out=new ArrayList<Point>();for(int i=0;i<Math.min(limit,values.size());i++)out.add(values.get(i).point);return out;
    }

    private static double[] coordinateMedian(List<Point> points) {
        List<Double>x=new ArrayList<Double>(),y=new ArrayList<Double>(),z=new ArrayList<Double>();
        for(Point p:points){x.add(p.x);y.add(p.y);z.add(p.z);}
        return new double[]{percentile(x,0.5),percentile(y,0.5),percentile(z,0.5)};
    }

    private static Candidate refine(List<Point> points, Candidate initial, double diagonal) {
        Candidate current=initial;
        for(int step=0;step<3;step++) {
            List<Projected> projected=project(points,current.origin,current.axis,current.basis);
            Circle fitted=leastSquaresCircle(projected,current.inlierIndices);
            if(fitted==null)break;
            Candidate next=evaluate(projected,current.axis,current.basis,current.origin,fitted,diagonal);
            if(next.inlierIndices.size()<30)break;
            current=next;
        }
        return current;
    }

    private static Candidate evaluate(List<Projected> points,double[] axis,Basis basis,
                                      double[] origin,Circle circle,double diagonal) {
        double threshold=Math.max(diagonal*0.006,circle.radius*0.04);
        List<Integer> inliers=new ArrayList<Integer>();
        List<Double> axial=new ArrayList<Double>();
        double residualSum=0;
        for(int index=0;index<points.size();index++) {
            Projected point=points.get(index);
            double radius=Math.hypot(point.u-circle.cx,point.v-circle.cy);
            double residual=Math.abs(radius-circle.radius);
            if(residual<=threshold) {
                inliers.add(index); axial.add(point.a); residualSum+=residual*residual;
            }
        }
        double low=percentile(axial,0.02),high=percentile(axial,0.98);
        double length=Math.max(0,high-low);
        double rms=inliers.isEmpty()?Double.POSITIVE_INFINITY:Math.sqrt(residualSum/inliers.size());
        double mid=(low+high)*0.5;
        double[] centerLine=new double[]{
                origin[0]+basis.u[0]*circle.cx+basis.v[0]*circle.cy+axis[0]*mid,
                origin[1]+basis.u[1]*circle.cx+basis.v[1]*circle.cy+axis[1]*mid,
                origin[2]+basis.u[2]*circle.cx+basis.v[2]*circle.cy+axis[2]*mid};
        double shape=Math.min(1.5,length/Math.max(circle.radius,1e-9));
        double score=inliers.size()*(0.75+0.25*Math.min(1.0,shape))
                /(1.0+rms/Math.max(threshold,1e-9));
        return new Candidate(axis.clone(),basis,origin.clone(),centerLine,
                circle.radius,length,rms,inliers,score);
    }

    private static Circle leastSquaresCircle(List<Projected> points,List<Integer> indices) {
        if(indices.size()<3)return null;
        double[][] normal=new double[3][3];
        double[] rhs=new double[3];
        for(int index:indices) {
            Projected p=points.get(index);
            double[] row={p.u,p.v,1.0};
            double value=-(p.u*p.u+p.v*p.v);
            for(int i=0;i<3;i++) {
                rhs[i]+=row[i]*value;
                for(int j=0;j<3;j++) normal[i][j]+=row[i]*row[j];
            }
        }
        double[] solution=solve3(normal,rhs);
        if(solution==null)return null;
        double cx=-solution[0]*0.5,cy=-solution[1]*0.5;
        double squared=cx*cx+cy*cy-solution[2];
        if(!(squared>0)||!Double.isFinite(squared))return null;
        return new Circle(cx,cy,Math.sqrt(squared));
    }

    private static Circle circle(Projected a,Projected b,Projected c) {
        double d=2.0*(a.u*(b.v-c.v)+b.u*(c.v-a.v)+c.u*(a.v-b.v));
        if(Math.abs(d)<1e-9)return null;
        double aa=a.u*a.u+a.v*a.v,bb=b.u*b.u+b.v*b.v,cc=c.u*c.u+c.v*c.v;
        double cx=(aa*(b.v-c.v)+bb*(c.v-a.v)+cc*(a.v-b.v))/d;
        double cy=(aa*(c.u-b.u)+bb*(a.u-c.u)+cc*(b.u-a.u))/d;
        double radius=Math.hypot(a.u-cx,a.v-cy);
        return Double.isFinite(radius)?new Circle(cx,cy,radius):null;
    }

    private static List<Projected> project(List<Point> points,double[] origin,double[] axis,Basis basis) {
        List<Projected> out=new ArrayList<Projected>(points.size());
        for(Point point:points) {
            double x=point.x-origin[0],y=point.y-origin[1],z=point.z-origin[2];
            out.add(new Projected(x*basis.u[0]+y*basis.u[1]+z*basis.u[2],
                    x*basis.v[0]+y*basis.v[1]+z*basis.v[2],
                    x*axis[0]+y*axis[1]+z*axis[2]));
        }
        return out;
    }

    private static Basis basis(double[] axis) {
        double[] helper=Math.abs(axis[2])<0.8?new double[]{0,0,1}:new double[]{0,1,0};
        double[] u=cross(helper,axis);normalize(u);
        double[] v=cross(axis,u);normalize(v);
        return new Basis(u,v);
    }

    private static Eigen covarianceEigen(List<Point> points,double[] center) {
        double[][] c=new double[3][3];
        for(Point p:points) {
            double x=p.x-center[0],y=p.y-center[1],z=p.z-center[2];
            c[0][0]+=x*x;c[0][1]+=x*y;c[0][2]+=x*z;
            c[1][0]+=x*y;c[1][1]+=y*y;c[1][2]+=y*z;
            c[2][0]+=x*z;c[2][1]+=y*z;c[2][2]+=z*z;
        }
        return jacobi(c,100);
    }
    private static Eigen jacobi(double[][] source,int sweeps) {
        double[][] a=copy(source),v={{1,0,0},{0,1,0},{0,0,1}};
        for(int iteration=0;iteration<sweeps*9;iteration++) {
            int p=0,q=1;double largest=0;
            for(int i=0;i<3;i++)for(int j=i+1;j<3;j++)if(Math.abs(a[i][j])>largest){largest=Math.abs(a[i][j]);p=i;q=j;}
            if(largest<1e-12)break;
            double phi=0.5*Math.atan2(2*a[p][q],a[q][q]-a[p][p]);
            double cs=Math.cos(phi),sn=Math.sin(phi);
            double pp=cs*cs*a[p][p]-2*sn*cs*a[p][q]+sn*sn*a[q][q];
            double qq=sn*sn*a[p][p]+2*sn*cs*a[p][q]+cs*cs*a[q][q];
            for(int k=0;k<3;k++)if(k!=p&&k!=q){double kp=a[k][p],kq=a[k][q];a[k][p]=a[p][k]=cs*kp-sn*kq;a[k][q]=a[q][k]=sn*kp+cs*kq;}
            a[p][p]=pp;a[q][q]=qq;a[p][q]=a[q][p]=0;
            for(int k=0;k<3;k++){double vp=v[k][p],vq=v[k][q];v[k][p]=cs*vp-sn*vq;v[k][q]=sn*vp+cs*vq;}
        }
        return new Eigen(new double[]{a[0][0],a[1][1],a[2][2]},v);
    }

    private static double[] solve3(double[][] a,double[] b) {
        double[][] m=new double[3][4];
        for(int i=0;i<3;i++){System.arraycopy(a[i],0,m[i],0,3);m[i][3]=b[i];}
        for(int col=0;col<3;col++) {
            int pivot=col;for(int row=col+1;row<3;row++)if(Math.abs(m[row][col])>Math.abs(m[pivot][col]))pivot=row;
            if(Math.abs(m[pivot][col])<1e-12)return null;
            double[] tmp=m[col];m[col]=m[pivot];m[pivot]=tmp;
            double d=m[col][col];for(int j=col;j<4;j++)m[col][j]/=d;
            for(int row=0;row<3;row++)if(row!=col){double f=m[row][col];for(int j=col;j<4;j++)m[row][j]-=f*m[col][j];}
        }
        return new double[]{m[0][3],m[1][3],m[2][3]};
    }

    private static double boundingDiagonal(List<Point> points) {
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
        for(Point p:points){minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);minZ=Math.min(minZ,p.z);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);maxZ=Math.max(maxZ,p.z);}
        double x=maxX-minX,y=maxY-minY,z=maxZ-minZ;return Math.sqrt(x*x+y*y+z*z);
    }
    private static double[] centroid(List<Point> points){double x=0,y=0,z=0;for(Point p:points){x+=p.x;y+=p.y;z+=p.z;}return new double[]{x/points.size(),y/points.size(),z/points.size()};}
    private static double[] column(double[][] matrix,int col){return new double[]{matrix[0][col],matrix[1][col],matrix[2][col]};}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static void normalize(double[] v){double n=Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);if(n>1e-12){v[0]/=n;v[1]/=n;v[2]/=n;}}
    private static void orient(double[] axis){int i=Math.abs(axis[1])>Math.abs(axis[0])?1:0;if(Math.abs(axis[2])>Math.abs(axis[i]))i=2;if(axis[i]<0){axis[0]=-axis[0];axis[1]=-axis[1];axis[2]=-axis[2];}}
    private static long next(long state){return state*6364136223846793005L+1442695040888963407L;}
    private static double percentile(List<Double> source,double q){if(source.isEmpty())return 0;List<Double>v=new ArrayList<Double>(source);Collections.sort(v);double p=Math.max(0,Math.min(1,q))*(v.size()-1);int lo=(int)Math.floor(p),hi=(int)Math.ceil(p);return lo==hi?v.get(lo):v.get(lo)*(hi-p)+v.get(hi)*(p-lo);}
    private static double[][] copy(double[][] source){double[][]r=new double[source.length][];for(int i=0;i<source.length;i++)r[i]=source[i].clone();return r;}

    public static final class Point {
        public final double x,y,z;
        public Point(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    }
    public static final class Result {
        public final boolean solved;
        public final String status;
        public final double[] axis;
        public final double[] centerLine;
        public final double radius;
        public final double length;
        public final double radialRms;
        public final List<Integer> inlierIndices;
        public final int totalPoints;
        public final double inlierRatio;
        Result(boolean solved,String status,double[] axis,double[] centerLine,double radius,double length,double radialRms,List<Integer> inliers,int total,double ratio){
            this.solved=solved;this.status=status;this.axis=axis==null?null:axis.clone();this.centerLine=centerLine==null?null:centerLine.clone();this.radius=radius;this.length=length;this.radialRms=radialRms;this.inlierIndices=Collections.unmodifiableList(new ArrayList<Integer>(inliers));this.totalPoints=total;this.inlierRatio=ratio;
        }
        static Result failed(String status){return new Result(false,status,null,null,Double.NaN,Double.NaN,Double.POSITIVE_INFINITY,Collections.<Integer>emptyList(),0,0);}
        public boolean ready(){return "STRONG".equals(status)||"USABLE".equals(status);}
        public String summary(){return "Manto Ø "+format(radius*2)+" · L "+format(length)+" · inliers "+inlierIndices.size()+"/"+totalPoints+" · RMS "+format(radialRms)+" · "+status;}
        private static String format(double v){return String.format(java.util.Locale.ROOT,"%.4f",v);}
    }
    private static final class Projected {final double u,v,a;Projected(double u,double v,double a){this.u=u;this.v=v;this.a=a;}}
    private static final class Circle {final double cx,cy,radius;Circle(double cx,double cy,double radius){this.cx=cx;this.cy=cy;this.radius=radius;}}
    private static final class Basis {final double[]u,v;Basis(double[]u,double[]v){this.u=u;this.v=v;}}
    private static final class Candidate {
        final double[]axis;final Basis basis;final double[]origin;final double[]centerLine;final double radius,length,radialRms,score;final List<Integer>inlierIndices;
        Candidate(double[]axis,Basis basis,double[]origin,double[]centerLine,double radius,double length,double radialRms,List<Integer>inliers,double score){this.axis=axis;this.basis=basis;this.origin=origin;this.centerLine=centerLine;this.radius=radius;this.length=length;this.radialRms=radialRms;this.inlierIndices=inliers;this.score=score;}
    }
    private static final class DistancePoint {final Point point;final double distance;DistancePoint(Point point,double distance){this.point=point;this.distance=distance;}}
    private static final class Eigen {final double[]values;final double[][]vectors;Eigen(double[]values,double[][]vectors){this.values=values;this.vectors=vectors;}}
}
