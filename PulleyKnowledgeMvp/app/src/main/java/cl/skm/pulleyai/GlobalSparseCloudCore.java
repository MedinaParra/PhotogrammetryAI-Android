package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fuses repeated triangulations of the same multiview track into a single sparse cloud. */
public final class GlobalSparseCloudCore {
    private GlobalSparseCloudCore() {}

    public static Result fuse(List<Sample> input, int minimumSupport) {
        int minSupport = Math.max(2, minimumSupport);
        Map<Integer,List<Sample>> groups = new HashMap<Integer,List<Sample>>();
        if (input != null) {
            for (Sample sample : input) {
                if (sample == null || sample.trackId < 0 || !sample.finite()
                        || !Double.isFinite(sample.reprojectionErrorPx)
                        || sample.reprojectionErrorPx < 0.0) continue;
                List<Sample> values = groups.get(sample.trackId);
                if (values == null) {
                    values = new ArrayList<Sample>();
                    groups.put(sample.trackId, values);
                }
                values.add(sample);
            }
        }
        List<FusedPoint> points = new ArrayList<FusedPoint>();
        int rejectedSamples = 0;
        int totalSupport = 0;
        double errorSum = 0.0;
        for (Map.Entry<Integer,List<Sample>> entry : groups.entrySet()) {
            List<Sample> values = entry.getValue();
            if (values.size() < minSupport) continue;
            double mx = median(values,0), my = median(values,1), mz = median(values,2);
            List<Double> distances = new ArrayList<Double>();
            for (Sample sample : values) distances.add(distance(sample,mx,my,mz));
            double medianDistance = percentile(distances,0.5);
            List<Double> deviations = new ArrayList<Double>();
            for (double distance : distances) deviations.add(Math.abs(distance-medianDistance));
            double mad = percentile(deviations,0.5);
            double threshold = Math.max(0.01, medianDistance + Math.max(0.015, 3.5*1.4826*mad));
            double sx=0,sy=0,sz=0,weightSum=0;
            List<Sample> accepted = new ArrayList<Sample>();
            for (int i=0;i<values.size();i++) {
                Sample sample=values.get(i);
                if (distances.get(i)>threshold) {
                    rejectedSamples++;
                    continue;
                }
                double weight=1.0/(0.25+sample.reprojectionErrorPx*sample.reprojectionErrorPx);
                sx+=weight*sample.x; sy+=weight*sample.y; sz+=weight*sample.z;
                weightSum+=weight; accepted.add(sample);
            }
            if (accepted.size()<minSupport || weightSum<=0) continue;
            double x=sx/weightSum,y=sy/weightSum,z=sz/weightSum;
            double spreadSum=0,reprojectionSum=0;
            for(Sample sample:accepted){
                double d=distance(sample,x,y,z);
                spreadSum+=d*d;
                reprojectionSum+=sample.reprojectionErrorPx*sample.reprojectionErrorPx;
            }
            double spread=Math.sqrt(spreadSum/accepted.size());
            double reprojection=Math.sqrt(reprojectionSum/accepted.size());
            points.add(new FusedPoint(entry.getKey(),x,y,z,accepted.size(),spread,reprojection));
            totalSupport+=accepted.size();
            errorSum+=reprojection*reprojection;
        }
        Collections.sort(points,new Comparator<FusedPoint>(){
            @Override public int compare(FusedPoint a,FusedPoint b){return Integer.compare(a.trackId,b.trackId);}
        });
        double averageSupport=points.isEmpty()?0.0:(double)totalSupport/points.size();
        double rmsReprojection=points.isEmpty()?Double.POSITIVE_INFINITY:
                Math.sqrt(errorSum/points.size());
        int stable=0;
        for(FusedPoint point:points) if(point.support>=3 && point.spatialSpread<=0.035) stable++;
        double stableRatio=points.isEmpty()?0.0:(double)stable/points.size();
        String status=points.size()>=180 && averageSupport>=3.0 && stableRatio>=0.72
                && rmsReprojection<=1.6 ? "STRONG"
                : points.size()>=70 && averageSupport>=2.2 && stableRatio>=0.50
                && rmsReprojection<=2.5 ? "USABLE" : "WEAK";
        return new Result(points,groups.size(),rejectedSamples,averageSupport,
                rmsReprojection,stableRatio,status);
    }

    private static double median(List<Sample> values,int axis){
        List<Double> numbers=new ArrayList<Double>();
        for(Sample sample:values) numbers.add(axis==0?sample.x:axis==1?sample.y:sample.z);
        return percentile(numbers,0.5);
    }
    private static double percentile(List<Double> source,double q){
        if(source.isEmpty()) return 0.0;
        List<Double> values=new ArrayList<Double>(source);
        Collections.sort(values);
        double p=Math.max(0,Math.min(1,q))*(values.size()-1);
        int lo=(int)Math.floor(p),hi=(int)Math.ceil(p);
        if(lo==hi)return values.get(lo);
        return values.get(lo)*(hi-p)+values.get(hi)*(p-lo);
    }
    private static double distance(Sample sample,double x,double y,double z){
        double dx=sample.x-x,dy=sample.y-y,dz=sample.z-z;
        return Math.sqrt(dx*dx+dy*dy+dz*dz);
    }

    public static final class Sample {
        public final int trackId;
        public final double x,y,z;
        public final double reprojectionErrorPx;
        public Sample(int trackId,double x,double y,double z,double reprojectionErrorPx){
            this.trackId=trackId;this.x=x;this.y=y;this.z=z;
            this.reprojectionErrorPx=reprojectionErrorPx;
        }
        boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    }
    public static final class FusedPoint {
        public final int trackId;
        public final double x,y,z;
        public final int support;
        public final double spatialSpread;
        public final double reprojectionRmsPx;
        FusedPoint(int trackId,double x,double y,double z,int support,
                   double spatialSpread,double reprojectionRmsPx){
            this.trackId=trackId;this.x=x;this.y=y;this.z=z;this.support=support;
            this.spatialSpread=spatialSpread;this.reprojectionRmsPx=reprojectionRmsPx;
        }
    }
    public static final class Result {
        public final List<FusedPoint> points;
        public final int candidateTracks;
        public final int rejectedSamples;
        public final double averageSupport;
        public final double rmsReprojectionPx;
        public final double stablePointRatio;
        public final String status;
        Result(List<FusedPoint> points,int candidateTracks,int rejectedSamples,
               double averageSupport,double rmsReprojectionPx,double stablePointRatio,String status){
            this.points=Collections.unmodifiableList(new ArrayList<FusedPoint>(points));
            this.candidateTracks=candidateTracks;this.rejectedSamples=rejectedSamples;
            this.averageSupport=averageSupport;this.rmsReprojectionPx=rmsReprojectionPx;
            this.stablePointRatio=stablePointRatio;this.status=status;
        }
        public boolean ready(){return "STRONG".equals(status)||"USABLE".equals(status);}
        public String summary(){
            return "Puntos "+points.size()+" · soporte "
                    +String.format(java.util.Locale.ROOT,"%.2f",averageSupport)
                    +" · estables "+Math.round(stablePointRatio*100.0)+"% · "+status;
        }
    }
}
