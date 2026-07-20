package cl.skm.pulleyai;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/** Tracks device orientation and short-term angular motion without owning any UI. */
public final class DevicePoseTracker implements SensorEventListener {
    public static final class Snapshot {
        public final double yaw;
        public final double pitch;
        public final double roll;
        public final double motion;

        Snapshot(double yaw, double pitch, double roll, double motion) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.motion = motion;
        }
    }

    private final SensorManager manager;
    private final Sensor rotationSensor;
    private final Sensor gyroscope;
    private final float[] rotation = new float[9];
    private final float[] angles = new float[3];
    private volatile double yaw;
    private volatile double pitch;
    private volatile double roll;
    private volatile double motion;

    public DevicePoseTracker(Context context) {
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (manager == null) {
            rotationSensor = null;
            gyroscope = null;
        } else {
            Sensor preferred = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            rotationSensor = preferred != null
                    ? preferred
                    : manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    public void start() {
        if (manager == null) return;
        if (rotationSensor != null) manager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        if (gyroscope != null) manager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        if (manager != null) manager.unregisterListener(this);
    }

    public Snapshot snapshot() {
        return new Snapshot(yaw, pitch, roll, motion);
    }

    public boolean hasOrientationSensor() {
        return rotationSensor != null;
    }

    public boolean hasGyroscope() {
        return gyroscope != null;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor == rotationSensor) {
            SensorManager.getRotationMatrixFromVector(rotation, event.values);
            SensorManager.getOrientation(rotation, angles);
            yaw = Math.toDegrees(angles[0]);
            pitch = Math.toDegrees(angles[1]);
            roll = Math.toDegrees(angles[2]);
        } else if (event.sensor == gyroscope) {
            double magnitude = Math.sqrt(
                    event.values[0] * event.values[0]
                            + event.values[1] * event.values[1]
                            + event.values[2] * event.values[2]
            );
            motion = 0.88 * motion + 0.12 * magnitude;
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
