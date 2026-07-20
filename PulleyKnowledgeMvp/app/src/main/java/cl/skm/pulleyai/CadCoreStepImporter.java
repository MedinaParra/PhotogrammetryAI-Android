package cl.skm.pulleyai;

import android.content.Context;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Optional adapter for the public FreeCAD-Native cadcore AAR without a hard source dependency. */
public final class CadCoreStepImporter {
    private static final String PROVIDER="com.medinaparra.freecadandroid.cadcore.AndroidCadCoreProvider";
    private static final String STEP_REQUEST="com.medinaparra.freecadandroid.cadcore.CadCoreRuntime$StepRequest";
    private CadCoreStepImporter() { }

    public static final class Status {
        public final boolean aarPresent;
        public final boolean stepReady;
        public final String runtime;
        public final String diagnostic;
        Status(boolean aarPresent,boolean stepReady,String runtime,String diagnostic){
            this.aarPresent=aarPresent;this.stepReady=stepReady;this.runtime=runtime;this.diagnostic=diagnostic;
        }
    }

    public static final class ImportResult {
        public final CadMeshCache.Mesh mesh;
        public final String runtime;
        public final String sourceSha256;
        ImportResult(CadMeshCache.Mesh mesh,String runtime,String sourceSha256){
            this.mesh=mesh;this.runtime=runtime;this.sourceSha256=sourceSha256;
        }
    }

    public static Status status(Context context){
        Object runtime=null;
        try{
            Class<?> provider=Class.forName(PROVIDER);
            Method create=provider.getMethod("create",Context.class);
            runtime=create.invoke(null,context.getApplicationContext());
            Object status=runtime.getClass().getMethod("status").invoke(runtime);
            String backend=stringField(status,"backend");
            String version=stringField(status,"version");
            String diagnostic=stringField(status,"diagnostic");
            Object capabilities=field(status,"capabilities");
            boolean step=capabilities!=null&&capabilities.toString().contains("STEP_IMPORT")
                    &&capabilities.toString().contains("TESSELLATION");
            return new Status(true,step,join(backend,version),diagnostic);
        }catch(ClassNotFoundException absent){
            return new Status(false,false,"PARAMETRIC_PREVIEW","cadcore AAR no está empaquetado");
        }catch(Throwable error){
            return new Status(true,false,"CADCORE_ERROR",message(error));
        }finally{close(runtime);}
    }

    public static ImportResult importStep(Context context,File file,double linearDeflection,
                                          double angularDeflectionDegrees)throws Exception{
        Object runtime=null;
        Object shape=null;
        try{
            Class<?> provider=Class.forName(PROVIDER);
            Method require=provider.getMethod("requireNativeStep",Context.class);
            runtime=require.invoke(null,context.getApplicationContext());
            Object runtimeStatus=runtime.getClass().getMethod("status").invoke(runtime);
            String runtimeName=join(stringField(runtimeStatus,"backend"),stringField(runtimeStatus,"version"));
            Class<?> requestClass=Class.forName(STEP_REQUEST);
            Constructor<?> constructor=requestClass.getConstructor(File.class,double.class,double.class);
            Object request=constructor.newInstance(file,linearDeflection,angularDeflectionDegrees);
            Method importMethod=findMethod(runtime.getClass(),"importStep",requestClass);
            shape=importMethod.invoke(runtime,request);
            if(shape==null)throw new IllegalStateException("cadcore devolvió una forma nula");
            Object meshObject=field(shape,"mesh");
            if(meshObject==null)throw new IllegalStateException("cadcore no devolvió teselación");
            float[] vertices=(float[])field(meshObject,"vertices");
            float[] normals=(float[])field(meshObject,"normals");
            int[] triangles=(int[])field(meshObject,"triangles");
            Object boundsObject=field(meshObject,"bounds");
            double[] bounds=boundsObject==null?null:new double[]{
                    doubleField(boundsObject,"minX"),doubleField(boundsObject,"minY"),doubleField(boundsObject,"minZ"),
                    doubleField(boundsObject,"maxX"),doubleField(boundsObject,"maxY"),doubleField(boundsObject,"maxZ")};
            String sha=(String)field(shape,"sourceSha256");
            return new ImportResult(new CadMeshCache.Mesh(vertices,normals,triangles,bounds),runtimeName,sha);
        }catch(InvocationTargetException wrapped){
            Throwable cause=wrapped.getCause()==null?wrapped:wrapped.getCause();
            throw new Exception(message(cause),cause);
        }catch(Exception error){throw error;}
        catch(Throwable error){throw new Exception(message(error),error);}
        finally{close(shape);close(runtime);}
    }

    private static Method findMethod(Class<?> type,String name,Class<?> parameter)throws NoSuchMethodException{
        try{return type.getMethod(name,parameter);}catch(NoSuchMethodException direct){
            for(Method method:type.getMethods())if(name.equals(method.getName())&&method.getParameterTypes().length==1)return method;
            throw direct;
        }
    }
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getField(name);return f.get(object);}
    private static String stringField(Object object,String name)throws Exception{Object value=field(object,name);return value==null?"":String.valueOf(value);}
    private static double doubleField(Object object,String name)throws Exception{return ((Number)field(object,name)).doubleValue();}
    private static void close(Object value){if(value==null)return;try{if(value instanceof AutoCloseable)((AutoCloseable)value).close();else value.getClass().getMethod("close").invoke(value);}catch(Throwable ignored){}}
    private static String join(String a,String b){return (a==null?"":a)+(b==null||b.isEmpty()?"":" "+b);}
    private static String message(Throwable error){Throwable current=error;while(current instanceof InvocationTargetException&&((InvocationTargetException)current).getCause()!=null)current=((InvocationTargetException)current).getCause();String text=current.getMessage();return current.getClass().getSimpleName()+(text==null?"":": "+text);}
}
