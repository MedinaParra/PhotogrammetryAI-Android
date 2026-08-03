package cl.skm.pulleyai;

import java.util.Locale;

/** Versioned camera profile and Brown-Conrady distortion math; profiles remain uncalibrated until sourced from a physical campaign. */
public final class CameraCalibrationProfileCore {
    private CameraCalibrationProfileCore() {}

    public enum ValidationState { UNCALIBRATED, LAB_ESTIMATED, VALIDATED }

    public static final class Profile {
        public final String profileId,deviceModel,cameraId,version,source;
        public final int width,height;
        public final double fx,fy,cx,cy,k1,k2,p1,p2,k3;
        public final ValidationState validationState;
        public final double reprojectionRmsPx;

        public Profile(String profileId,String deviceModel,String cameraId,String version,
                       int width,int height,double fx,double fy,double cx,double cy,
                       double k1,double k2,double p1,double p2,double k3,
                       ValidationState validationState,double reprojectionRmsPx,String source) {
            this.profileId=required(profileId,"profileId");
            this.deviceModel=required(deviceModel,"deviceModel");
            this.cameraId=required(cameraId,"cameraId");
            this.version=required(version,"version");
            if(width<=0||height<=0)throw new IllegalArgumentException("invalid resolution");
            if(!positive(fx)||!positive(fy)||!finite(cx)||!finite(cy))throw new IllegalArgumentException("invalid intrinsics");
            if(!finite(k1)||!finite(k2)||!finite(p1)||!finite(p2)||!finite(k3))throw new IllegalArgumentException("invalid distortion");
            if(validationState==null)throw new IllegalArgumentException("validationState required");
            if(!Double.isNaN(reprojectionRmsPx)&&(!finite(reprojectionRmsPx)||reprojectionRmsPx<0))throw new IllegalArgumentException("invalid rms");
            this.width=width;this.height=height;this.fx=fx;this.fy=fy;this.cx=cx;this.cy=cy;
            this.k1=k1;this.k2=k2;this.p1=p1;this.p2=p2;this.k3=k3;
            this.validationState=validationState;this.reprojectionRmsPx=reprojectionRmsPx;
            this.source=required(source,"source");
        }

        public Profile scaled(int targetWidth,int targetHeight) {
            if(targetWidth<=0||targetHeight<=0)throw new IllegalArgumentException("invalid target resolution");
            double sx=targetWidth/(double)width,sy=targetHeight/(double)height;
            return new Profile(profileId,deviceModel,cameraId,version,targetWidth,targetHeight,
                    fx*sx,fy*sy,cx*sx,cy*sy,k1,k2,p1,p2,k3,validationState,
                    reprojectionRmsPx,source);
        }

        public Point distortPixel(double undistortedU,double undistortedV) {
            double x=(undistortedU-cx)/fx,y=(undistortedV-cy)/fy;
            Point normalized=distortNormalized(x,y);
            return new Point(normalized.x*fx+cx,normalized.y*fy+cy);
        }

        public Point undistortPixel(double distortedU,double distortedV) {
            double xd=(distortedU-cx)/fx,yd=(distortedV-cy)/fy;
            double x=xd,y=yd;
            for(int i=0;i<15;i++) {
                double r2=x*x+y*y,r4=r2*r2,r6=r4*r2;
                double radial=1+k1*r2+k2*r4+k3*r6;
                if(Math.abs(radial)<1e-10||!finite(radial))break;
                double tx=2*p1*x*y+p2*(r2+2*x*x);
                double ty=p1*(r2+2*y*y)+2*p2*x*y;
                double nx=(xd-tx)/radial,ny=(yd-ty)/radial;
                if(Math.hypot(nx-x,ny-y)<1e-13){x=nx;y=ny;break;}
                x=nx;y=ny;
            }
            return new Point(x*fx+cx,y*fy+cy);
        }

        public Point distortNormalized(double x,double y) {
            if(!finite(x)||!finite(y))throw new IllegalArgumentException("invalid point");
            double r2=x*x+y*y,r4=r2*r2,r6=r4*r2;
            double radial=1+k1*r2+k2*r4+k3*r6;
            double tx=2*p1*x*y+p2*(r2+2*x*x);
            double ty=p1*(r2+2*y*y)+2*p2*x*y;
            return new Point(x*radial+tx,y*radial+ty);
        }

        public boolean suitableForAutomaticCorrection() {
            return validationState==ValidationState.VALIDATED
                    && finite(reprojectionRmsPx)&&reprojectionRmsPx<=1.0;
        }

        public String fingerprint() {
            String raw=profileId+'|'+deviceModel+'|'+cameraId+'|'+version+'|'+width+'x'+height+'|'
                    +String.format(Locale.ROOT,"%.9f|%.9f|%.9f|%.9f|%.12f|%.12f|%.12f|%.12f|%.12f",
                    fx,fy,cx,cy,k1,k2,p1,p2,k3)+'|'+validationState+'|'+source;
            long hash=0xcbf29ce484222325L;for(int i=0;i<raw.length();i++){hash^=raw.charAt(i);hash*=0x100000001b3L;}
            String hex=Long.toHexString(hash);StringBuilder out=new StringBuilder(64);while(out.length()<64)out.append(hex);return out.substring(0,64);
        }
    }

    public static final class Point {public final double x,y;public Point(double x,double y){this.x=x;this.y=y;}}
    private static String required(String value,String name){if(value==null||value.trim().isEmpty())throw new IllegalArgumentException(name+" required");return value.trim();}
    private static boolean finite(double value){return Double.isFinite(value);}
    private static boolean positive(double value){return finite(value)&&value>0;}
}
