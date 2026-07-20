package cl.skm.pulleyai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Deterministic AP214 faceted BRep used to validate the Android STEP kernel. */
public final class StepSelfTestModel {
    public static final double SIZE_X_MM=100.0;
    public static final double SIZE_Y_MM=50.0;
    public static final double SIZE_Z_MM=20.0;
    private StepSelfTestModel() { }

    public static String boxStep(){
        StringBuilder s=new StringBuilder(8192);
        s.append("ISO-10303-21;\nHEADER;\n")
                .append("FILE_DESCRIPTION(('SKM CAD kernel self test'),'2;1');\n")
                .append("FILE_NAME('skm_kernel_box.step','2026-07-20T00:00:00',('SKM Industrial'),('SKM Industrial'),'SKM Polea AI','OCCT Android','');\n")
                .append("FILE_SCHEMA(('AUTOMOTIVE_DESIGN { 1 0 10303 214 3 1 1 }'));\nENDSEC;\nDATA;\n");

        point(s,1,0,0,0);point(s,2,SIZE_X_MM,0,0);point(s,3,SIZE_X_MM,SIZE_Y_MM,0);point(s,4,0,SIZE_Y_MM,0);
        point(s,5,0,0,SIZE_Z_MM);point(s,6,SIZE_X_MM,0,SIZE_Z_MM);point(s,7,SIZE_X_MM,SIZE_Y_MM,SIZE_Z_MM);point(s,8,0,SIZE_Y_MM,SIZE_Z_MM);
        direction(s,9,0,0,-1);direction(s,10,0,0,1);direction(s,11,1,0,0);
        direction(s,12,0,-1,0);direction(s,13,0,1,0);direction(s,14,-1,0,0);direction(s,15,1,0,0);direction(s,16,0,1,0);

        plane(s,20,21,1,9,11);   // bottom
        plane(s,22,23,5,10,11);  // top
        plane(s,24,25,1,12,11);  // front
        plane(s,26,27,4,13,11);  // back
        plane(s,28,29,1,14,16);  // left
        plane(s,30,31,2,15,16);  // right

        face(s,40,41,42,new int[]{1,4,3,2},21);
        face(s,43,44,45,new int[]{5,6,7,8},23);
        face(s,46,47,48,new int[]{1,2,6,5},25);
        face(s,49,50,51,new int[]{4,8,7,3},27);
        face(s,52,53,54,new int[]{1,5,8,4},29);
        face(s,55,56,57,new int[]{2,3,7,6},31);

        s.append("#60=CLOSED_SHELL('SKM_SELFTEST_SHELL',(#42,#45,#48,#51,#54,#57));\n")
                .append("#61=FACETED_BREP('SKM_SELFTEST_BOX',#60);\n")
                .append("#70=(LENGTH_UNIT()NAMED_UNIT(*)SI_UNIT(.MILLI.,.METRE.));\n")
                .append("#71=(NAMED_UNIT(*)PLANE_ANGLE_UNIT()SI_UNIT($,.RADIAN.));\n")
                .append("#72=(NAMED_UNIT(*)SI_UNIT($,.STERADIAN.)SOLID_ANGLE_UNIT());\n")
                .append("#73=UNCERTAINTY_MEASURE_WITH_UNIT(LENGTH_MEASURE(1.E-7),#70,'distance_accuracy_value','');\n")
                .append("#74=(GEOMETRIC_REPRESENTATION_CONTEXT(3)GLOBAL_UNCERTAINTY_ASSIGNED_CONTEXT((#73))GLOBAL_UNIT_ASSIGNED_CONTEXT((#70,#71,#72))REPRESENTATION_CONTEXT('SKM_SELFTEST','3D'));\n")
                .append("#75=AXIS2_PLACEMENT_3D('',#1,#10,#11);\n")
                .append("#76=ADVANCED_BREP_SHAPE_REPRESENTATION('SKM_SELFTEST_REP',(#61,#75),#74);\n")
                .append("#80=APPLICATION_CONTEXT('mechanical design');\n")
                .append("#81=APPLICATION_PROTOCOL_DEFINITION('international standard','automotive_design',2000,#80);\n")
                .append("#82=PRODUCT_CONTEXT('',#80,'mechanical');\n")
                .append("#83=PRODUCT('SKM_SELFTEST_BOX','SKM_SELFTEST_BOX','',(#82));\n")
                .append("#84=PRODUCT_DEFINITION_FORMATION('','',#83);\n")
                .append("#85=PRODUCT_DEFINITION_CONTEXT('part definition',#80,'design');\n")
                .append("#86=PRODUCT_DEFINITION('design','',#84,#85);\n")
                .append("#87=PRODUCT_DEFINITION_SHAPE('','',#86);\n")
                .append("#88=SHAPE_DEFINITION_REPRESENTATION(#87,#76);\n")
                .append("#89=PRODUCT_RELATED_PRODUCT_CATEGORY('part','',(#83));\n")
                .append("ENDSEC;\nEND-ISO-10303-21;\n");
        return s.toString();
    }

    public static boolean structurallyValid(String step){
        if(step==null||!step.startsWith("ISO-10303-21;")||!step.trim().endsWith("END-ISO-10303-21;"))return false;
        return occurrences(step,"ADVANCED_FACE(")==6
                &&occurrences(step,"POLY_LOOP(")==6
                &&occurrences(step,"CARTESIAN_POINT(")==8
                &&step.contains("FACETED_BREP('SKM_SELFTEST_BOX'")
                &&step.contains("SI_UNIT(.MILLI.,.METRE.)");
    }

    public static String sha256(){
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] bytes=digest.digest(boxStep().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(64);for(byte value:bytes)hex.append(String.format(Locale.ROOT,"%02x",value&255));return hex.toString();
        }catch(Exception error){throw new IllegalStateException(error);}
    }

    private static void point(StringBuilder s,int id,double x,double y,double z){s.append('#').append(id).append("=CARTESIAN_POINT('',(").append(f(x)).append(',').append(f(y)).append(',').append(f(z)).append("));\n");}
    private static void direction(StringBuilder s,int id,double x,double y,double z){s.append('#').append(id).append("=DIRECTION('',(").append(f(x)).append(',').append(f(y)).append(',').append(f(z)).append("));\n");}
    private static void plane(StringBuilder s,int placement,int plane,int point,int normal,int reference){s.append('#').append(placement).append("=AXIS2_PLACEMENT_3D('',#").append(point).append(",#").append(normal).append(",#").append(reference).append(");\n#").append(plane).append("=PLANE('',#").append(placement).append(");\n");}
    private static void face(StringBuilder s,int loop,int bound,int face,int[] points,int plane){s.append('#').append(loop).append("=POLY_LOOP('',(");for(int i=0;i<points.length;i++){if(i>0)s.append(',');s.append('#').append(points[i]);}s.append("));\n#").append(bound).append("=FACE_OUTER_BOUND('',#").append(loop).append(",.T.);\n#").append(face).append("=ADVANCED_FACE('',(#").append(bound).append("),#").append(plane).append(",.T.);\n");}
    private static int occurrences(String value,String token){int count=0,index=0;while((index=value.indexOf(token,index))>=0){count++;index+=token.length();}return count;}
    private static String f(double value){return String.format(Locale.ROOT,"%.6f",value);}
}
