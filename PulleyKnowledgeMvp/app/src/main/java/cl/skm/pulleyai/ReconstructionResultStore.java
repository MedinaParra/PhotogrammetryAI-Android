package cl.skm.pulleyai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Small independent store for the latest qualified reconstruction result of each capture session. */
public final class ReconstructionResultStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "reconstruction_results.db";
    private static final int DB_VERSION = 1;

    public ReconstructionResultStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE result(session_id TEXT PRIMARY KEY,updated_at INTEGER NOT NULL,"+
                "status TEXT NOT NULL,selected_frames INTEGER NOT NULL,discarded_frames INTEGER NOT NULL,"+
                "candidate_pairs INTEGER NOT NULL,track_count INTEGER NOT NULL,pose_nodes INTEGER NOT NULL,"+
                "cloud_points INTEGER NOT NULL,shell_inliers INTEGER NOT NULL,diameter_mm REAL,"+
                "diameter_uncertainty_mm REAL,confidence REAL NOT NULL DEFAULT 0,model_ready INTEGER NOT NULL,"+
                "report_path TEXT NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion!=newVersion)throw new IllegalStateException("Missing result migration "+oldVersion+" -> "+newVersion);
    }

    public void save(String sessionId, SessionOverlapAnalyzer.Report report, String reportPath) {
        ContentValues row=new ContentValues();
        row.put("session_id",sessionId);
        row.put("updated_at",System.currentTimeMillis());
        row.put("status",report.modelReady?"METRIC_SHELL_READY":report.ready?"GLOBAL_SPARSE_READY":"INCOMPLETE");
        row.put("selected_frames",report.analyzedFrames);
        row.put("discarded_frames",report.selection.discarded);
        row.put("candidate_pairs",report.candidatePairs);
        row.put("track_count",report.tracks.tracks.size());
        row.put("pose_nodes",report.globalPoseGraph.reachedNodes);
        row.put("cloud_points",report.globalCloud.points.size());
        row.put("shell_inliers",report.shell.inlierIndices.size());
        if(Double.isFinite(report.metric.shellDiameterMm))row.put("diameter_mm",report.metric.shellDiameterMm);else row.putNull("diameter_mm");
        if(Double.isFinite(report.metric.diameterUncertaintyMm))row.put("diameter_uncertainty_mm",report.metric.diameterUncertaintyMm);else row.putNull("diameter_uncertainty_mm");
        row.put("confidence",report.metric.confidence);
        row.put("model_ready",report.modelReady?1:0);
        row.put("report_path",reportPath);
        getWritableDatabase().insertWithOnConflict("result",null,row,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Snapshot find(String sessionId) {
        Cursor cursor=getReadableDatabase().rawQuery(
                "SELECT status,updated_at,selected_frames,discarded_frames,candidate_pairs,"+
                        "track_count,pose_nodes,cloud_points,shell_inliers,diameter_mm,"+
                        "diameter_uncertainty_mm,confidence,model_ready,report_path FROM result WHERE session_id=?",
                new String[]{sessionId});
        try {
            if(!cursor.moveToFirst())return null;
            return new Snapshot(cursor.getString(0),cursor.getLong(1),cursor.getInt(2),cursor.getInt(3),
                    cursor.getInt(4),cursor.getInt(5),cursor.getInt(6),cursor.getInt(7),cursor.getInt(8),
                    cursor.isNull(9)?null:cursor.getDouble(9),cursor.isNull(10)?null:cursor.getDouble(10),
                    cursor.getDouble(11),cursor.getInt(12)!=0,cursor.getString(13));
        } finally { cursor.close(); }
    }

    public static final class Snapshot {
        public final String status;
        public final long updatedAt;
        public final int selectedFrames,discardedFrames,candidatePairs,trackCount,poseNodes,cloudPoints,shellInliers;
        public final Double diameterMm,diameterUncertaintyMm;
        public final double confidence;
        public final boolean modelReady;
        public final String reportPath;
        Snapshot(String status,long updatedAt,int selectedFrames,int discardedFrames,int candidatePairs,
                 int trackCount,int poseNodes,int cloudPoints,int shellInliers,Double diameterMm,
                 Double diameterUncertaintyMm,double confidence,boolean modelReady,String reportPath) {
            this.status=status;this.updatedAt=updatedAt;this.selectedFrames=selectedFrames;
            this.discardedFrames=discardedFrames;this.candidatePairs=candidatePairs;
            this.trackCount=trackCount;this.poseNodes=poseNodes;this.cloudPoints=cloudPoints;
            this.shellInliers=shellInliers;this.diameterMm=diameterMm;
            this.diameterUncertaintyMm=diameterUncertaintyMm;this.confidence=confidence;
            this.modelReady=modelReady;this.reportPath=reportPath;
        }
    }
}
