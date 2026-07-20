package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Dependency-free mechanical checks for pulley assemblies whose principal axis is local X. */
public final class AssemblyConstraintCore {
    private AssemblyConstraintCore() { }

    public enum Severity { INFO, WARNING, BLOCKING }

    public static final class Part {
        public final String id;
        public final String type;
        public final double length, diameter, width, height, depth, bore;
        public final double tx, ty, tz, rx, ry, rz;
        public final boolean locked;

        public Part(String id, String type, double length, double diameter,
                    double width, double height, double depth, double bore,
                    double tx, double ty, double tz, double rx, double ry,
                    double rz, boolean locked) {
            this.id=id; this.type=type; this.length=length; this.diameter=diameter;
            this.width=width; this.height=height; this.depth=depth; this.bore=bore;
            this.tx=tx; this.ty=ty; this.tz=tz; this.rx=rx; this.ry=ry;
            this.rz=rz; this.locked=locked;
        }

        public boolean type(String expected) { return expected.equals(type); }
        public boolean coaxialType() {
            return type("SHELL") || type("SHAFT") || type("LOCKING_SLEEVE")
                    || type("BEARING") || type("HUB") || type("COUPLING");
        }
        public double axialSize() { return length > 0 ? length : width; }
    }

    public static final class Issue {
        public final Severity severity;
        public final String code;
        public final String partId;
        public final String message;

        Issue(Severity severity, String code, String partId, String message) {
            this.severity=severity; this.code=code; this.partId=partId; this.message=message;
        }
    }

    public static final class Suggestion {
        public final String partId;
        public final double tx,ty,tz,rx,ry,rz;
        public final String reason;

        Suggestion(String partId,double tx,double ty,double tz,
                   double rx,double ry,double rz,String reason) {
            this.partId=partId;this.tx=tx;this.ty=ty;this.tz=tz;
            this.rx=rx;this.ry=ry;this.rz=rz;this.reason=reason;
        }
    }

    public static final class Result {
        public final List<Issue> issues;
        public final List<Suggestion> suggestions;
        public final int blocking;
        public final int warnings;

        Result(List<Issue> issues,List<Suggestion> suggestions) {
            this.issues=Collections.unmodifiableList(new ArrayList<Issue>(issues));
            this.suggestions=Collections.unmodifiableList(new ArrayList<Suggestion>(suggestions));
            int b=0,w=0;for(Issue issue:issues){if(issue.severity==Severity.BLOCKING)b++;else if(issue.severity==Severity.WARNING)w++;}
            blocking=b;warnings=w;
        }
        public boolean ready(){return blocking==0;}
        public String summary(){return blocking+" bloqueos · "+warnings+" advertencias · "+suggestions.size()+" ajustes sugeridos";}
    }

    public static Result evaluate(List<Part> source) {
        List<Part> parts=source==null?Collections.<Part>emptyList():source;
        List<Issue> issues=new ArrayList<Issue>();
        List<Suggestion> suggestions=new ArrayList<Suggestion>();
        Part shell=first(parts,"SHELL");
        Part shaft=first(parts,"SHAFT");
        List<Part> supports=all(parts,"SUPPORT");

        if(shell==null)issues.add(issue(Severity.BLOCKING,"SHELL_MISSING",null,"Falta el manto de referencia."));
        if(shaft==null)issues.add(issue(Severity.BLOCKING,"SHAFT_MISSING",null,"Falta el eje del conjunto."));
        if(supports.size()<2)issues.add(issue(Severity.WARNING,"SUPPORT_PAIR_INCOMPLETE",null,"Se esperan al menos dos soportes."));

        if(shell!=null){
            if(!(shell.length>0&&shell.diameter>0))issues.add(issue(Severity.BLOCKING,"SHELL_DIMENSIONS_INVALID",shell.id,"El manto necesita largo y diámetro positivos."));
            for(Part part:parts){
                if(part==shell||!part.coaxialType())continue;
                double radialOffset=Math.hypot(part.ty-shell.ty,part.tz-shell.tz);
                double angleOffset=Math.max(angleDistance(part.ry,shell.ry),angleDistance(part.rz,shell.rz));
                double tolerance=Math.max(1.0,Math.min(8.0,shell.diameter*0.002));
                if(radialOffset>tolerance||angleOffset>0.75){
                    issues.add(issue(Severity.WARNING,"COAXIALITY",part.id,
                            String.format(Locale.ROOT,"Desalineado %.2f mm / %.2f° respecto del manto.",radialOffset,angleOffset)));
                    if(!part.locked)suggestions.add(new Suggestion(part.id,part.tx,shell.ty,shell.tz,
                            part.rx,shell.ry,shell.rz,"Alinear con el eje del manto"));
                }
            }
        }

        if(shell!=null&&shaft!=null){
            if(shaft.length+1e-6<shell.length){
                issues.add(issue(Severity.BLOCKING,"SHAFT_TOO_SHORT",shaft.id,
                        "El eje no puede ser más corto que el manto."));
            }
            if(shaft.diameter<=0||shaft.diameter>=shell.diameter){
                issues.add(issue(Severity.BLOCKING,"SHAFT_DIAMETER_INVALID",shaft.id,
                        "El diámetro del eje debe ser positivo y menor que el manto."));
            }
            double shaftMin=shaft.tx-shaft.length/2,shaftMax=shaft.tx+shaft.length/2;
            for(Part part:parts){
                if(!(part.type("LOCKING_SLEEVE")||part.type("BEARING")||part.type("HUB")||part.type("COUPLING")))continue;
                double half=part.axialSize()/2;
                if(part.tx-half<shaftMin-1||part.tx+half>shaftMax+1){
                    issues.add(issue(Severity.WARNING,"OUTSIDE_SHAFT",part.id,
                            "El componente queda parcial o totalmente fuera del eje."));
                }
                if(part.bore>0){
                    double clearance=part.bore-shaft.diameter;
                    if(clearance<-0.05){
                        issues.add(issue(Severity.BLOCKING,"BORE_SMALLER_THAN_SHAFT",part.id,
                                String.format(Locale.ROOT,"Agujero Ø %.2f menor que eje Ø %.2f.",part.bore,shaft.diameter)));
                    }else if(clearance>Math.max(5.0,shaft.diameter*0.04)){
                        issues.add(issue(Severity.WARNING,"BORE_EXCESSIVE",part.id,
                                String.format(Locale.ROOT,"Holgura diametral %.2f mm; revisar selección.",clearance)));
                    }
                }
            }
        }

        if(shell!=null&&supports.size()>=2){
            Collections.sort(supports,new Comparator<Part>(){
                @Override public int compare(Part a,Part b){return Double.compare(a.tx,b.tx);}
            });
            Part left=supports.get(0),right=supports.get(supports.size()-1);
            double symmetry=Math.abs((left.tx+right.tx)*0.5-shell.tx);
            double tolerance=Math.max(3.0,shell.length*0.005);
            if(symmetry>tolerance){
                issues.add(issue(Severity.WARNING,"SUPPORT_SYMMETRY",null,
                        String.format(Locale.ROOT,"El par de soportes está descentrado %.2f mm.",symmetry)));
                double leftHalf=Math.max(1,left.width)/2;
                double rightHalf=Math.max(1,right.width)/2;
                double margin=Math.max(80.0,shell.diameter*0.12);
                if(!left.locked)suggestions.add(new Suggestion(left.id,
                        shell.tx-shell.length/2-margin-leftHalf,left.ty,left.tz,left.rx,left.ry,left.rz,
                        "Ubicar soporte izquierdo simétricamente"));
                if(!right.locked)suggestions.add(new Suggestion(right.id,
                        shell.tx+shell.length/2+margin+rightHalf,right.ty,right.tz,right.rx,right.ry,right.rz,
                        "Ubicar soporte derecho simétricamente"));
            }
            double shellMin=shell.tx-shell.length/2,shellMax=shell.tx+shell.length/2;
            for(Part support:supports){
                double half=Math.max(1,support.width)/2;
                if(support.tx+half>shellMin&&support.tx-half<shellMax){
                    issues.add(issue(Severity.WARNING,"SUPPORT_OVERLAPS_SHELL",support.id,
                            "El volumen simplificado del soporte intersecta el manto."));
                }
            }
        }

        return new Result(issues,suggestions);
    }

    private static Part first(List<Part> parts,String type){for(Part part:parts)if(part.type(type))return part;return null;}
    private static List<Part> all(List<Part> parts,String type){List<Part> result=new ArrayList<Part>();for(Part part:parts)if(part.type(type))result.add(part);return result;}
    private static Issue issue(Severity severity,String code,String part,String message){return new Issue(severity,code,part,message);}
    private static double angleDistance(double a,double b){double d=(a-b)%360.0;if(d>180)d-=360;if(d<-180)d+=360;return Math.abs(d);}
}
