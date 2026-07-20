import cl.skm.pulleyai.AssemblyConstraintCore;

import java.util.ArrayList;
import java.util.List;

public final class AssemblyConstraintsV41Test {
    public static void main(String[] args) {
        List<AssemblyConstraintCore.Part> parts=new ArrayList<AssemblyConstraintCore.Part>();
        parts.add(part("shell","SHELL",1520,800,0,0,0,0,0,0,0,0,0,0,false));
        parts.add(part("shaft","SHAFT",2400,220,0,0,0,0,0,12,-5,0,0,1.2,false));
        parts.add(part("left","SUPPORT",0,0,360,520,240,0,-1080,0,-280,0,0,0,true));
        parts.add(part("right","SUPPORT",0,0,360,520,240,0,930,0,-280,0,0,0,false));
        parts.add(part("sleeve","LOCKING_SLEEVE",180,300,0,0,0,200,900,5,8,0,0,0,false));

        AssemblyConstraintCore.Result result=AssemblyConstraintCore.evaluate(parts);
        require(result.blocking>=1,"small sleeve bore must block the assembly");
        require(hasIssue(result,"BORE_SMALLER_THAN_SHAFT"),"bore/shaft issue missing");
        require(hasIssue(result,"COAXIALITY"),"coaxiality issue missing");
        require(hasIssue(result,"SUPPORT_SYMMETRY"),"support symmetry issue missing");
        require(hasSuggestion(result,"shaft"),"misaligned shaft must receive suggestion");
        require(hasSuggestion(result,"sleeve"),"misaligned sleeve must receive suggestion");
        require(!hasSuggestion(result,"left"),"locked support must never move");
        require(hasSuggestion(result,"right"),"unlocked support must receive symmetry suggestion");

        List<AssemblyConstraintCore.Part> valid=new ArrayList<AssemblyConstraintCore.Part>();
        valid.add(part("shell","SHELL",1520,800,0,0,0,0,0,0,0,0,0,0,false));
        valid.add(part("shaft","SHAFT",2400,220,0,0,0,0,0,0,0,0,0,0,false));
        valid.add(part("left","SUPPORT",0,0,360,520,240,0,-1040,0,-280,0,0,0,false));
        valid.add(part("right","SUPPORT",0,0,360,520,240,0,1040,0,-280,0,0,0,false));
        valid.add(part("sleeve","LOCKING_SLEEVE",180,300,0,0,0,220,900,0,0,0,0,0,false));
        AssemblyConstraintCore.Result clean=AssemblyConstraintCore.evaluate(valid);
        require(clean.blocking==0,"valid assembly must have no blockers: "+clean.summary());
        System.out.println("Assembly constraints v41 OK · invalid "+result.summary()+" · valid "+clean.summary());
    }

    private static AssemblyConstraintCore.Part part(String id,String type,double length,double diameter,
            double width,double height,double depth,double bore,double tx,double ty,double tz,
            double rx,double ry,double rz,boolean locked){
        return new AssemblyConstraintCore.Part(id,type,length,diameter,width,height,depth,bore,
                tx,ty,tz,rx,ry,rz,locked);
    }
    private static boolean hasIssue(AssemblyConstraintCore.Result result,String code){for(AssemblyConstraintCore.Issue issue:result.issues)if(code.equals(issue.code))return true;return false;}
    private static boolean hasSuggestion(AssemblyConstraintCore.Result result,String id){for(AssemblyConstraintCore.Suggestion suggestion:result.suggestions)if(id.equals(suggestion.partId))return true;return false;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
