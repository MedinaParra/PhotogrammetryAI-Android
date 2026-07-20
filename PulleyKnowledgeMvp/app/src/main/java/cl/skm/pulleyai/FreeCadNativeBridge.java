package cl.skm.pulleyai;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Bridge matching the public FreeCAD-Native cadcore ABI. */
public final class FreeCadNativeBridge {
    public static final long CAP_STEP_IMPORT = 1L << 0;
    public static final long CAP_TESSELLATION = 1L << 1;
    public static final long CAP_BOUNDING_BOX = 1L << 2;
    public static final long CAP_RIGID_TRANSFORM = 1L << 3;
    public static final long CAP_STEP_EXPORT = 1L << 4;

    public static final class Status {
        public final boolean loaded;
        public final String runtimeInfo;
        public final long capabilities;
        public final String diagnostic;
        Status(boolean loaded,String runtimeInfo,long capabilities,String diagnostic){
            this.loaded=loaded;this.runtimeInfo=runtimeInfo;this.capabilities=capabilities;this.diagnostic=diagnostic;
        }
        public boolean supports(long capability){return (capabilities&capability)!=0;}
        public boolean supportsStep(){return supports(CAP_STEP_IMPORT)&&supports(CAP_TESSELLATION);}
        public String label(){return loaded?runtimeInfo:"PARAMETRIC_PREVIEW";}
    }

    private static volatile Status cached;
    private FreeCadNativeBridge() {}

    public static Status status() {
        Status current=cached;if(current!=null)return current;
        synchronized(FreeCadNativeBridge.class){
            if(cached!=null)return cached;
            try{
                System.loadLibrary("freecad_android_bridge");
                cached=new Status(true,nativeRuntimeInfo(),nativeCapabilitiesMask(),nativeLastError());
            }catch(Throwable error){
                cached=new Status(false,"PARAMETRIC_PREVIEW",0,
                        error.getClass().getSimpleName()+": "+safe(error.getMessage()));
            }
            return cached;
        }
    }

    public static String validateStep(File file) throws Exception {
        if(file==null||!file.isFile())throw new IllegalArgumentException("Archivo STEP inexistente");
        String lower=file.getName().toLowerCase(Locale.ROOT);
        if(!(lower.endsWith(".step")||lower.endsWith(".stp")))throw new IllegalArgumentException("Seleccione un archivo .step o .stp");
        byte[] head=new byte[1024];int count;
        try(BufferedInputStream input=new BufferedInputStream(new FileInputStream(file))){count=input.read(head);}
        String text=new String(head,0,Math.max(0,count),StandardCharsets.US_ASCII);
        if(!text.contains("ISO-10303-21")||!text.contains("HEADER"))throw new IllegalArgumentException("Cabecera STEP ISO-10303-21 inválida");
        return sha256(file);
    }

    public static boolean rigid(double[] m,double tolerance){
        if(m==null||m.length!=16)return false;
        double nx=m[0]*m[0]+m[4]*m[4]+m[8]*m[8];
        double ny=m[1]*m[1]+m[5]*m[5]+m[9]*m[9];
        double nz=m[2]*m[2]+m[6]*m[6]+m[10]*m[10];
        double xy=m[0]*m[1]+m[4]*m[5]+m[8]*m[9];
        double xz=m[0]*m[2]+m[4]*m[6]+m[8]*m[10];
        double yz=m[1]*m[2]+m[5]*m[6]+m[9]*m[10];
        return Math.abs(nx-1)<=tolerance&&Math.abs(ny-1)<=tolerance&&Math.abs(nz-1)<=tolerance
                &&Math.abs(xy)<=tolerance&&Math.abs(xz)<=tolerance&&Math.abs(yz)<=tolerance
                &&Math.abs(m[12])<=tolerance&&Math.abs(m[13])<=tolerance
                &&Math.abs(m[14])<=tolerance&&Math.abs(m[15]-1)<=tolerance;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int count;
        try(FileInputStream input=new FileInputStream(file)){while((count=input.read(buffer))>=0)if(count>0)digest.update(buffer,0,count);}
        StringBuilder result=new StringBuilder();for(byte value:digest.digest())result.append(String.format(Locale.ROOT,"%02x",value));return result.toString();
    }

    private static String safe(String value){return value==null?"":value;}
    private static native String nativeRuntimeInfo();
    private static native long nativeCapabilitiesMask();
    private static native String nativeLastError();
}
