package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Selects a bounded, high-quality and angularly balanced frame set for reconstruction. */
public final class ReconstructionFrameSelectorCore {
    private ReconstructionFrameSelectorCore() {}

    public static Result select(List<Candidate> source, int maxPerCell, int maxTotal) {
        int cellLimit=Math.max(1,Math.min(4,maxPerCell));
        int totalLimit=Math.max(24,Math.min(96,maxTotal));
        Map<String,List<Candidate>> cells=new HashMap<String,List<Candidate>>();
        if(source!=null) {
            for(Candidate candidate:source) {
                if(candidate==null || candidate.sector<0 || candidate.sector>=12
                        || candidate.band==null || !candidate.accepted
                        || !Double.isFinite(candidate.score())) continue;
                String key=cell(candidate.band,candidate.sector);
                List<Candidate> values=cells.get(key);
                if(values==null){values=new ArrayList<Candidate>();cells.put(key,values);}
                values.add(candidate);
            }
        }
        Comparator<Candidate> quality=new Comparator<Candidate>(){
            @Override public int compare(Candidate a,Candidate b){
                int score=Double.compare(b.score(),a.score());
                return score!=0?score:Long.compare(a.createdAt,b.createdAt);
            }
        };
        for(List<Candidate> values:cells.values())Collections.sort(values,quality);
        List<Candidate> selected=new ArrayList<Candidate>();
        Set<Integer> selectedIds=new HashSet<Integer>();
        List<String> keys=new ArrayList<String>(cells.keySet());
        Collections.sort(keys);
        for(String key:keys) {
            if(selected.size()>=totalLimit)break;
            Candidate best=cells.get(key).get(0);
            selected.add(best);selectedIds.add(best.id);
        }
        List<Candidate> extras=new ArrayList<Candidate>();
        for(String key:keys) {
            List<Candidate> values=cells.get(key);
            for(int i=1;i<Math.min(cellLimit,values.size());i++)extras.add(values.get(i));
        }
        Collections.sort(extras,quality);
        for(Candidate candidate:extras) {
            if(selected.size()>=totalLimit)break;
            if(selectedIds.add(candidate.id))selected.add(candidate);
        }
        Collections.sort(selected,new Comparator<Candidate>(){
            @Override public int compare(Candidate a,Candidate b){return Integer.compare(a.id,b.id);}
        });
        int low=0,high=0;
        for(Candidate candidate:selected) {
            if("HIGH".equals(candidate.band))high|=1<<candidate.sector;
            else low|=1<<candidate.sector;
        }
        int available=0;
        for(List<Candidate> values:cells.values())available+=values.size();
        int discarded=Math.max(0,available-selected.size());
        int completeMask=(1<<12)-1;
        String status=low==completeMask && high==completeMask && selected.size()>=30
                ? "BALANCED" : selected.size()>=24 ? "PARTIAL" : "INSUFFICIENT";
        return new Result(selected,available,discarded,low,high,status);
    }

    private static String cell(String band,int sector){
        return ("HIGH".equals(band)?"H":"L")+sector;
    }

    public static final class Candidate {
        public final int id;
        public final String band;
        public final int sector;
        public final double blur;
        public final double luma;
        public final double motion;
        public final long createdAt;
        public final boolean accepted;
        public Candidate(int id,String band,int sector,double blur,double luma,double motion,
                         long createdAt,boolean accepted) {
            this.id=id;this.band=band;this.sector=sector;this.blur=blur;this.luma=luma;
            this.motion=motion;this.createdAt=createdAt;this.accepted=accepted;
        }
        public double score(){
            double sharpness=Math.log1p(Math.max(0.0,blur));
            double exposure=Math.abs(luma-128.0)/128.0;
            return sharpness-1.25*exposure-2.2*Math.max(0.0,motion);
        }
    }
    public static final class Result {
        public final List<Candidate> selected;
        public final int available;
        public final int discarded;
        public final int lowMask;
        public final int highMask;
        public final String status;
        Result(List<Candidate> selected,int available,int discarded,int lowMask,int highMask,String status) {
            this.selected=Collections.unmodifiableList(new ArrayList<Candidate>(selected));
            this.available=available;this.discarded=discarded;this.lowMask=lowMask;
            this.highMask=highMask;this.status=status;
        }
        public boolean ready(){return "BALANCED".equals(status)||"PARTIAL".equals(status);}
        public String summary(){return "Seleccionadas "+selected.size()+"/"+available
                +" · descartadas "+discarded+" · "+status;}
    }
}
