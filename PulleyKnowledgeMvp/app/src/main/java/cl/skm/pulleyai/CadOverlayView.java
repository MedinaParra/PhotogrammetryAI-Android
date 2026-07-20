package cl.skm.pulleyai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Lightweight engineering overlay for parametric parts and qualified shell geometry. */
public final class CadOverlayView extends View {
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<CadAssemblyStore.Component> components=new ArrayList<CadAssemblyStore.Component>();
    private Double referenceLengthMm;
    private Double referenceDiameterMm;
    private double viewYawDeg=-28;
    private double viewPitchDeg=18;
    private double zoom=0.22;
    private float lastX,lastY;
    private double pinchDistance;
    private boolean dragging;

    public CadOverlayView(Context context) {
        super(context);
        line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(dp(1.6f));
        text.setTextSize(dp(12));text.setColor(Color.WHITE);
        setBackgroundColor(Color.rgb(19,26,32));
    }

    public void setAssembly(List<CadAssemblyStore.Component> components,
                            Double referenceLengthMm,Double referenceDiameterMm) {
        this.components=components==null?new ArrayList<CadAssemblyStore.Component>()
                :new ArrayList<CadAssemblyStore.Component>(components);
        this.referenceLengthMm=referenceLengthMm;this.referenceDiameterMm=referenceDiameterMm;
        autoFit();invalidate();
    }

    public void resetView(){viewYawDeg=-28;viewPitchDeg=18;autoFit();invalidate();}

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawGrid(canvas);
        if(referenceLengthMm!=null&&referenceLengthMm>0&&referenceDiameterMm!=null&&referenceDiameterMm>0){
            line.setColor(Color.argb(180,80,235,160));line.setStrokeWidth(dp(2));
            line.setPathEffect(new DashPathEffect(new float[]{dp(8),dp(5)},0));
            drawCylinder(canvas,referenceLengthMm,referenceDiameterMm,0,0,0,0,0,0,line,24);
            line.setPathEffect(null);
            text.setColor(Color.rgb(120,240,180));
            canvas.drawText("Reconstrucción: manto métrico",dp(12),dp(22),text);
        } else {
            text.setColor(Color.LTGRAY);
            canvas.drawText("Sin manto métrico reconstruido",dp(12),dp(22),text);
        }

        int pending=0;
        for(CadAssemblyStore.Component component:components){
            if(!component.visible)continue;
            if(component.sourceKind==CadAssemblyStore.SourceKind.STEP){pending++;continue;}
            line.setColor(component.colorArgb);line.setStrokeWidth(dp(component.locked?2.6f:1.7f));
            if(isCylinder(component.type)){
                drawCylinder(canvas,component.lengthMm,component.diameterMm,
                        component.txMm,component.tyMm,component.tzMm,
                        component.rxDeg,component.ryDeg,component.rzDeg,line,20);
                if(component.boreMm>0){
                    Paint bore=new Paint(line);bore.setAlpha(150);
                    drawCylinder(canvas,component.lengthMm,component.boreMm,
                            component.txMm,component.tyMm,component.tzMm,
                            component.rxDeg,component.ryDeg,component.rzDeg,bore,16);
                }
            }else{
                drawBox(canvas,component.widthMm,component.heightMm,component.depthMm,
                        component.txMm,component.tyMm,component.tzMm,
                        component.rxDeg,component.ryDeg,component.rzDeg,line);
            }
        }
        if(pending>0){
            text.setColor(Color.rgb(255,190,95));
            canvas.drawText(pending+" STEP registrado(s), esperando kernel nativo",dp(12),getHeight()-dp(14),text);
        }
        drawAxis(canvas);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                lastX=event.getX();lastY=event.getY();dragging=true;return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if(event.getPointerCount()>=2)pinchDistance=distance(event);return true;
            case MotionEvent.ACTION_MOVE:
                if(event.getPointerCount()>=2){
                    double current=distance(event);if(pinchDistance>10&&current>10){zoom*=current/pinchDistance;zoom=Math.max(0.015,Math.min(4.0,zoom));}
                    pinchDistance=current;invalidate();return true;
                }
                if(dragging){float x=event.getX(),y=event.getY();viewYawDeg+=(x-lastX)*0.35;viewPitchDeg+=(y-lastY)*0.30;
                    viewPitchDeg=Math.max(-85,Math.min(85,viewPitchDeg));lastX=x;lastY=y;invalidate();}return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging=false;pinchDistance=0;return true;
            default:return true;
        }
    }

    private void drawGrid(Canvas canvas){
        Paint grid=new Paint(line);grid.setColor(Color.rgb(45,57,64));grid.setStrokeWidth(dp(1));
        double span=4000;for(int i=-4;i<=4;i++){double offset=i*span/4;
            drawSegment(canvas,new double[]{-span,offset,0},new double[]{span,offset,0},grid);
            drawSegment(canvas,new double[]{offset,-span,0},new double[]{offset,span,0},grid);}
    }

    private void drawAxis(Canvas canvas){
        Paint axis=new Paint(line);axis.setStrokeWidth(dp(2.2f));
        axis.setColor(Color.rgb(245,90,90));drawSegment(canvas,new double[]{0,0,0},new double[]{600,0,0},axis);
        axis.setColor(Color.rgb(90,220,120));drawSegment(canvas,new double[]{0,0,0},new double[]{0,600,0},axis);
        axis.setColor(Color.rgb(90,150,245));drawSegment(canvas,new double[]{0,0,0},new double[]{0,0,600},axis);
    }

    private void drawCylinder(Canvas canvas,double length,double diameter,
                              double tx,double ty,double tz,double rx,double ry,double rz,
                              Paint paint,int segments){
        if(!(length>0&&diameter>0))return;double radius=diameter/2,half=length/2;
        double[][] left=new double[segments][3],right=new double[segments][3];
        for(int i=0;i<segments;i++){double angle=2*Math.PI*i/segments;
            left[i]=transform(-half,radius*Math.cos(angle),radius*Math.sin(angle),tx,ty,tz,rx,ry,rz);
            right[i]=transform(half,radius*Math.cos(angle),radius*Math.sin(angle),tx,ty,tz,rx,ry,rz);}
        drawLoop(canvas,left,paint);drawLoop(canvas,right,paint);
        for(int i=0;i<segments;i+=Math.max(1,segments/8))drawSegment(canvas,left[i],right[i],paint);
    }

    private void drawBox(Canvas canvas,double width,double height,double depth,
                         double tx,double ty,double tz,double rx,double ry,double rz,Paint paint){
        if(!(width>0&&height>0&&depth>0))return;double x=width/2,y=height/2,z=depth/2;
        double[][] p={{-x,-y,-z},{x,-y,-z},{x,y,-z},{-x,y,-z},{-x,-y,z},{x,-y,z},{x,y,z},{-x,y,z}};
        for(int i=0;i<p.length;i++)p[i]=transform(p[i][0],p[i][1],p[i][2],tx,ty,tz,rx,ry,rz);
        int[][] edges={{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for(int[] edge:edges)drawSegment(canvas,p[edge[0]],p[edge[1]],paint);
    }

    private void drawLoop(Canvas canvas,double[][] points,Paint paint){
        if(points.length<2)return;Path path=new Path();float[] first=project(points[0]);path.moveTo(first[0],first[1]);
        for(int i=1;i<points.length;i++){float[] q=project(points[i]);path.lineTo(q[0],q[1]);}path.close();canvas.drawPath(path,paint);
    }

    private void drawSegment(Canvas canvas,double[] a,double[] b,Paint paint){float[] p=project(a),q=project(b);canvas.drawLine(p[0],p[1],q[0],q[1],paint);}

    private double[] transform(double x,double y,double z,double tx,double ty,double tz,double rx,double ry,double rz){
        double ax=Math.toRadians(rx),ay=Math.toRadians(ry),az=Math.toRadians(rz);
        double cy=Math.cos(ax),sy=Math.sin(ax);double y1=cy*y-sy*z,z1=sy*y+cy*z;
        double cp=Math.cos(ay),sp=Math.sin(ay);double x2=cp*x+sp*z1,z2=-sp*x+cp*z1;
        double cr=Math.cos(az),sr=Math.sin(az);double x3=cr*x2-sr*y1,y3=sr*x2+cr*y1;
        return new double[]{x3+tx,y3+ty,z2+tz};
    }

    private float[] project(double[] point){
        double yaw=Math.toRadians(viewYawDeg),pitch=Math.toRadians(viewPitchDeg);
        double x=Math.cos(yaw)*point[0]-Math.sin(yaw)*point[1];
        double y0=Math.sin(yaw)*point[0]+Math.cos(yaw)*point[1];
        double y=Math.cos(pitch)*y0-Math.sin(pitch)*point[2];
        double z=Math.sin(pitch)*y0+Math.cos(pitch)*point[2];
        double perspective=1.0/(1.0+Math.max(-0.75,z*zoom/7000.0));
        return new float[]{(float)(getWidth()/2.0+x*zoom*perspective),(float)(getHeight()/2.0-y*zoom*perspective)};
    }

    private void autoFit(){double max=1000;
        if(referenceLengthMm!=null)max=Math.max(max,referenceLengthMm);if(referenceDiameterMm!=null)max=Math.max(max,referenceDiameterMm);
        for(CadAssemblyStore.Component c:components)max=Math.max(max,Math.max(c.lengthMm,Math.max(c.diameterMm,Math.max(c.widthMm,Math.max(c.heightMm,c.depthMm))))+Math.abs(c.txMm)+Math.abs(c.tyMm)+Math.abs(c.tzMm));
        if(getWidth()>0&&getHeight()>0)zoom=Math.max(0.015,Math.min(2.5,0.72*Math.min(getWidth(),getHeight())/max));
    }

    private static boolean isCylinder(CadAssemblyStore.Type type){return type!=CadAssemblyStore.Type.SUPPORT&&type!=CadAssemblyStore.Type.STEP_OTHER;}
    private static double distance(MotionEvent event){double dx=event.getX(0)-event.getX(1),dy=event.getY(0)-event.getY(1);return Math.sqrt(dx*dx+dy*dy);}
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
}
