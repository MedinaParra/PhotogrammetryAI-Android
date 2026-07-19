package cl.skm.pulleyai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Independent, non-destructive store for capture sessions and full-resolution frames. */
public final class CaptureStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "capture_sessions.db";
    private static final int DB_VERSION = 2;

    public static final class Session {
        public final String id;
        public final String label;
        public final String status;
        public final String code;
        public final String ot;
        public final Double shellLengthMm;
        public final int accepted;
        public final int rejected;
        public final int lowMask;
        public final int highMask;

        Session(String id, String label, String status, String code, String ot, Double shellLengthMm,
                int accepted, int rejected, int lowMask, int highMask) {
            this.id = id;
            this.label = label;
            this.status = status;
            this.code = code;
            this.ot = ot;
            this.shellLengthMm = shellLengthMm;
            this.accepted = accepted;
            this.rejected = rejected;
            this.lowMask = lowMask;
            this.highMask = highMask;
        }
    }

    public static final class Frame {
        public final int sequence;
        public final String filePath;
        public final long createdAt;
        public final double yaw;
        public final double pitch;
        public final double roll;
        public final Long exposureNs;
        public final Integer iso;
        public final Float focusDistance;
        public final int width;
        public final int height;
        public final double blur;
        public final double luma;
        public final double motion;
        public final String quality;
        public final String reason;
        public final String band;
        public final int sector;
        public final String sha256;
        public final String cameraId;
        public final Integer sensorOrientation;
        public final Integer jpegOrientation;
        public final Float focalLengthMm;

        Frame(int sequence, String filePath, long createdAt, double yaw, double pitch, double roll,
              Long exposureNs, Integer iso, Float focusDistance, int width, int height,
              double blur, double luma, double motion, String quality, String reason, String band,
              int sector, String sha256, String cameraId, Integer sensorOrientation,
              Integer jpegOrientation, Float focalLengthMm) {
            this.sequence = sequence;
            this.filePath = filePath;
            this.createdAt = createdAt;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.exposureNs = exposureNs;
            this.iso = iso;
            this.focusDistance = focusDistance;
            this.width = width;
            this.height = height;
            this.blur = blur;
            this.luma = luma;
            this.motion = motion;
            this.quality = quality;
            this.reason = reason;
            this.band = band;
            this.sector = sector;
            this.sha256 = sha256;
            this.cameraId = cameraId;
            this.sensorOrientation = sensorOrientation;
            this.jpegOrientation = jpegOrientation;
            this.focalLengthMm = focalLengthMm;
        }
    }

    private final Context appContext;

    public CaptureStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        appContext = context.getApplicationContext();
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE session(id TEXT PRIMARY KEY,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL," +
                "status TEXT NOT NULL,label TEXT NOT NULL,material_code TEXT,ot TEXT,shell_length_mm REAL," +
                "accepted INTEGER NOT NULL DEFAULT 0,rejected INTEGER NOT NULL DEFAULT 0," +
                "low_mask INTEGER NOT NULL DEFAULT 0,high_mask INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE frame(id TEXT PRIMARY KEY,session_id TEXT NOT NULL,seq INTEGER NOT NULL," +
                "file_path TEXT NOT NULL,created_at INTEGER NOT NULL,yaw REAL NOT NULL,pitch REAL NOT NULL,roll REAL NOT NULL," +
                "exposure_ns INTEGER,iso INTEGER,focus_distance REAL,width INTEGER NOT NULL,height INTEGER NOT NULL," +
                "blur REAL NOT NULL,luma REAL NOT NULL,dark_fraction REAL NOT NULL,bright_fraction REAL NOT NULL," +
                "motion REAL NOT NULL,quality TEXT NOT NULL,reason TEXT NOT NULL,band TEXT NOT NULL," +
                "sector INTEGER NOT NULL CHECK(sector BETWEEN 0 AND 11),sha256 TEXT NOT NULL," +
                "camera_id TEXT,sensor_orientation INTEGER,jpeg_orientation INTEGER,focal_length_mm REAL," +
                "UNIQUE(session_id,seq),FOREIGN KEY(session_id) REFERENCES session(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX frame_session_idx ON frame(session_id,seq)");
        db.execSQL("CREATE INDEX frame_hash_idx ON frame(sha256)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE frame ADD COLUMN camera_id TEXT");
            db.execSQL("ALTER TABLE frame ADD COLUMN sensor_orientation INTEGER");
            db.execSQL("ALTER TABLE frame ADD COLUMN jpeg_orientation INTEGER");
            db.execSQL("ALTER TABLE frame ADD COLUMN focal_length_mm REAL");
        }
        if (newVersion > 2) {
            throw new IllegalStateException("Missing capture migration " + oldVersion + " -> " + newVersion);
        }
    }

    public String createSession(String label, String code, String ot, Double shellLengthMm) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        ContentValues row = new ContentValues();
        row.put("id", id);
        row.put("created_at", now);
        row.put("updated_at", now);
        row.put("status", "CAPTURING");
        row.put("label", clean(label, "Levantamiento de polea"));
        putNullable(row, "material_code", cleanNullable(code));
        putNullable(row, "ot", cleanNullable(ot));
        if (shellLengthMm == null) row.putNull("shell_length_mm");
        else row.put("shell_length_mm", shellLengthMm);
        getWritableDatabase().insertOrThrow("session", null, row);
        sessionDir(id);
        return id;
    }

    public Session getSession(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,label,status,material_code,ot,shell_length_mm,accepted,rejected,low_mask,high_mask " +
                        "FROM session WHERE id=?", new String[]{id});
        try { return c.moveToFirst() ? readSession(c) : null; }
        finally { c.close(); }
    }

    public Session latestOpen() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,label,status,material_code,ot,shell_length_mm,accepted,rejected,low_mask,high_mask " +
                        "FROM session WHERE status='CAPTURING' ORDER BY updated_at DESC LIMIT 1", null);
        try { return c.moveToFirst() ? readSession(c) : null; }
        finally { c.close(); }
    }

    public List<Session> recent(int limit) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,label,status,material_code,ot,shell_length_mm,accepted,rejected,low_mask,high_mask " +
                        "FROM session ORDER BY updated_at DESC LIMIT ?",
                new String[]{Integer.toString(Math.max(1, Math.min(50, limit)))});
        List<Session> out = new ArrayList<Session>();
        try { while (c.moveToNext()) out.add(readSession(c)); }
        finally { c.close(); }
        return out;
    }

    public int nextSequence(String sessionId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(MAX(seq),0)+1 FROM frame WHERE session_id=?", new String[]{sessionId});
        try { return c.moveToFirst() ? c.getInt(0) : 1; }
        finally { c.close(); }
    }

    public void saveFrame(String sessionId, int seq, File file, double yaw, double pitch, double roll,
                          Long exposureNs, Integer iso, Float focusDistance, ImageQualityAnalyzer.Result q,
                          String band, int sector, String sha256, String cameraId, int sensorOrientation,
                          int jpegOrientation, Float focalLengthMm) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues frame = new ContentValues();
            frame.put("id", UUID.randomUUID().toString());
            frame.put("session_id", sessionId);
            frame.put("seq", seq);
            frame.put("file_path", file.getAbsolutePath());
            frame.put("created_at", System.currentTimeMillis());
            frame.put("yaw", yaw);
            frame.put("pitch", pitch);
            frame.put("roll", roll);
            putNullable(frame, "exposure_ns", exposureNs);
            putNullable(frame, "iso", iso);
            putNullable(frame, "focus_distance", focusDistance);
            frame.put("width", q.width);
            frame.put("height", q.height);
            frame.put("blur", q.blurScore);
            frame.put("luma", q.meanLuma);
            frame.put("dark_fraction", q.darkFraction);
            frame.put("bright_fraction", q.brightFraction);
            frame.put("motion", q.motionScore);
            frame.put("quality", q.status);
            frame.put("reason", q.reason);
            frame.put("band", band);
            frame.put("sector", sector);
            frame.put("sha256", sha256);
            putNullable(frame, "camera_id", cameraId);
            frame.put("sensor_orientation", sensorOrientation);
            frame.put("jpeg_orientation", jpegOrientation);
            putNullable(frame, "focal_length_mm", focalLengthMm);
            db.insertOrThrow("frame", null, frame);

            Session current = querySession(db, sessionId);
            if (current == null) throw new IllegalStateException("Unknown session " + sessionId);
            int accepted = current.accepted;
            int rejected = current.rejected;
            int low = current.lowMask;
            int high = current.highMask;
            if (q.accepted()) {
                accepted++;
                if ("HIGH".equals(band)) high = CoveragePlanner.addSector(high, sector);
                else low = CoveragePlanner.addSector(low, sector);
            } else rejected++;
            ContentValues update = new ContentValues();
            update.put("updated_at", System.currentTimeMillis());
            update.put("accepted", accepted);
            update.put("rejected", rejected);
            update.put("low_mask", low);
            update.put("high_mask", high);
            db.update("session", update, "id=?", new String[]{sessionId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Frame> frames(String sessionId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT seq,file_path,created_at,yaw,pitch,roll,exposure_ns,iso,focus_distance,width,height," +
                        "blur,luma,motion,quality,reason,band,sector,sha256,camera_id,sensor_orientation,jpeg_orientation,focal_length_mm " +
                        "FROM frame WHERE session_id=? ORDER BY seq", new String[]{sessionId});
        List<Frame> out = new ArrayList<Frame>();
        try {
            while (c.moveToNext()) {
                out.add(new Frame(
                        c.getInt(0), c.getString(1), c.getLong(2), c.getDouble(3), c.getDouble(4), c.getDouble(5),
                        c.isNull(6) ? null : c.getLong(6), c.isNull(7) ? null : c.getInt(7),
                        c.isNull(8) ? null : c.getFloat(8), c.getInt(9), c.getInt(10),
                        c.getDouble(11), c.getDouble(12), c.getDouble(13), c.getString(14), c.getString(15),
                        c.getString(16), c.getInt(17), c.getString(18), c.isNull(19) ? null : c.getString(19),
                        c.isNull(20) ? null : c.getInt(20), c.isNull(21) ? null : c.getInt(21),
                        c.isNull(22) ? null : c.getFloat(22)));
            }
        } finally { c.close(); }
        return out;
    }

    public void finishSession(String id) {
        ContentValues row = new ContentValues();
        row.put("status", "CAPTURED");
        row.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("session", row, "id=?", new String[]{id});
    }

    public File frameFile(String sessionId, int seq) {
        return new File(sessionDir(sessionId), String.format(java.util.Locale.ROOT, "frame_%04d.jpg", seq));
    }

    File sessionDir(String id) {
        File dir = new File(new File(appContext.getFilesDir(), "capture_sessions"), id);
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("Cannot create " + dir);
        }
        return dir;
    }

    private static Session querySession(SQLiteDatabase db, String id) {
        Cursor c = db.rawQuery(
                "SELECT id,label,status,material_code,ot,shell_length_mm,accepted,rejected,low_mask,high_mask " +
                        "FROM session WHERE id=?", new String[]{id});
        try { return c.moveToFirst() ? readSession(c) : null; }
        finally { c.close(); }
    }

    private static Session readSession(Cursor c) {
        return new Session(c.getString(0), c.getString(1), c.getString(2), nullable(c, 3), nullable(c, 4),
                c.isNull(5) ? null : c.getDouble(5), c.getInt(6), c.getInt(7), c.getInt(8), c.getInt(9));
    }

    private static String nullable(Cursor c, int index) {
        return c.isNull(index) ? "" : c.getString(index);
    }

    private static String clean(String value, String fallback) {
        String clean = cleanNullable(value);
        return clean == null ? fallback : clean;
    }

    private static String cleanNullable(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private static void putNullable(ContentValues row, String key, Object value) {
        if (value == null) row.putNull(key);
        else if (value instanceof Long) row.put(key, (Long) value);
        else if (value instanceof Integer) row.put(key, (Integer) value);
        else if (value instanceof Float) row.put(key, (Float) value);
        else if (value instanceof Double) row.put(key, (Double) value);
        else if (value instanceof String) row.put(key, (String) value);
        else row.put(key, value.toString());
    }
}
