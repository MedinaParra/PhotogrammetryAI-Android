import cl.skm.pulleyai.ReconstructionFrameSelectorCore;
import java.util.ArrayList;
import java.util.List;

public final class ReconstructionFrameSelectorV38Test {
    public static void main(String[] args) {
        List<ReconstructionFrameSelectorCore.Candidate> values=
                new ArrayList<ReconstructionFrameSelectorCore.Candidate>();
        int id=0;
        for(String band:new String[]{"LOW","HIGH"}) {
            for(int sector=0;sector<12;sector++) {
                values.add(new ReconstructionFrameSelectorCore.Candidate(
                        id++,band,sector,150+sector,128,0.03,id,true));
                values.add(new ReconstructionFrameSelectorCore.Candidate(
                        id++,band,sector,500+sector,125,0.02,id,true));
                values.add(new ReconstructionFrameSelectorCore.Candidate(
                        id++,band,sector,900+sector,40,1.2,id,true));
            }
        }
        ReconstructionFrameSelectorCore.Result result=
                ReconstructionFrameSelectorCore.select(values,2,48);
        if(!"BALANCED".equals(result.status))throw new AssertionError(result.summary());
        if(result.selected.size()!=48)throw new AssertionError("selected="+result.selected.size());
        if(result.discarded!=24)throw new AssertionError("discarded="+result.discarded);
        java.util.HashMap<String,Integer> count=new java.util.HashMap<String,Integer>();
        for(ReconstructionFrameSelectorCore.Candidate c:result.selected){
            String key=c.band+c.sector;count.put(key,count.containsKey(key)?count.get(key)+1:1);
            if(c.motion>1.0)throw new AssertionError("poor motion frame selected");
        }
        if(count.size()!=24)throw new AssertionError("cells="+count.size());
        for(Integer n:count.values())if(n!=2)throw new AssertionError("cell count="+n);
        System.out.println("ReconstructionFrameSelectorV38Test OK: "+result.summary());
    }
}
