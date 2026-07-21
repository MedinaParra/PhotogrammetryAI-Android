package cl.skm.pulleyai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Local versioned storage for camera profiles; uncalibrated profiles are never selected for automatic correction. */
public final class CameraCalibrationProfileStore extends SQLiteOpenHelper {
    private static final String DB_NAME="camera_calibration_profiles.db";
    private static final int DB_VERSION=1;

    public CameraCalibrationProfileStore(Context context){super(context.getApplicationContext(),DB_NAME,null,DB_VERSION);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE camera_profile("+
                "profile_id TEXT NOT NULL,"+
                "version TEXT NOT NULL,"+
                "fingerprint TEXT NOT NULL UNIQUE,"+
                "device_model TEXT NOT NULL,"+
                "camera_id TEXT NOT NULL,"+
                "width INTEGER NOT NULL,"+
                "height INTEGER NOT NULL,"+
                "fx REAL NOT NULL,fy REAL NOT NULL,cx REAL NOT NULL,cy REAL NOT NULL,"+
                "k1 REAL NOT NULL,k2 REAL NOT NULL,p1 REAL NOT NULL,p2 REAL NOT NULL,k3 REAL NOT NULL,"+
                "validation_state TEXT NOT NULL,"+
                "reprojection_rms_px REAL,"+
                "source TEXT NOT NULL,"+
                "created_at INTEGER NOT NULL,"+
                "PRIMARY KEY(profile_id,version))");
        db.execSQL("CREATE INDEX idx_camera_profile_lookup ON camera_profile(device_model,camera_id,validation_state,width,height)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        throw new IllegalStateException("Camera profile migrations must be explicit and non-destructive");
    }

    public void save(CameraCalibrationProfileCore.Profile profile){
        if(profile==null)throw new IllegalArgumentException("profile required");
        ContentValues row=new ContentValues();
        row.put("profile_id",profile.profileId);row.put("version",profile.version);row.put("fingerprint",profile.fingerprint());
        row.put("device_model",profile.deviceModel);row.put("camera_id",profile.cameraId);row.put("width",profile.width);row.put("height",profile.height);
        row.put("fx",profile.fx);row.put("fy",profile.fy);row.put("cx",profile.cx);row.put("cy",profile.cy);
        row.put("k1",profile.k1);row.put("k2",profile.k2);row.put("p1",profile.p1);row.put("p2",profile.p2);row.put("k3",profile.k3);
        row.put("validation_state",profile.validationState.name());
        if(Double.isNaN(profile.reprojectionRmsPx))row.putNull("reprojection_rms_px");else row.put("reprojection_rms_px",profile.reprojectionRmsPx);
        row.put("source",profile.source);row.put("created_at",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("camera_profile",null,row,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public CameraCalibrationProfileCore.Profile findForAutomaticCorrection(String deviceModel,String cameraId,int width,int height){
        Cursor cursor=getReadableDatabase().rawQuery(
                "SELECT profile_id,device_model,camera_id,version,width,height,fx,fy,cx,cy,k1,k2,p1,p2,k3,validation_state,reprojection_rms_px,source "+
                        "FROM camera_profile WHERE device_model=? AND camera_id=? AND validation_state='VALIDATED' "+
                        "AND reprojection_rms_px IS NOT NULL AND reprojection_rms_px<=1.0 ORDER BY created_at DESC",
                new String[]{deviceModel,cameraId});
        try{
            while(cursor.moveToNext()){
                CameraCalibrationProfileCore.Profile stored=read(cursor);
                CameraCalibrationProfileCore.Profile scaled=stored.width==width&&stored.height==height?stored:stored.scaled(width,height);
                if(scaled.suitableForAutomaticCorrection())return scaled;
            }
            return null;
        }finally{cursor.close();}
    }

    public int count(){Cursor cursor=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM camera_profile",null);try{return cursor.moveToFirst()?cursor.getInt(0):0;}finally{cursor.close();}}

    private static CameraCalibrationProfileCore.Profile read(Cursor c){
        double rms=c.isNull(16)?Double.NaN:c.getDouble(16);
        return new CameraCalibrationProfileCore.Profile(c.getString(0),c.getString(1),c.getString(2),c.getString(3),
                c.getInt(4),c.getInt(5),c.getDouble(6),c.getDouble(7),c.getDouble(8),c.getDouble(9),
                c.getDouble(10),c.getDouble(11),c.getDouble(12),c.getDouble(13),c.getDouble(14),
                CameraCalibrationProfileCore.ValidationState.valueOf(c.getString(15)),rms,c.getString(17));
    }
}
