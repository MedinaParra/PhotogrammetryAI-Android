package cl.ingenieria.photogrammetryai.core.foundation;

import java.util.Arrays;
import java.util.Objects;

/**
 * Dependency-free 3D math used by the reconstruction core.
 *
 * <p>All distances are expressed in millimetres. A {@link RigidPose} maps a point from a
 * local coordinate system into its parent coordinate system:</p>
 *
 * <pre>p_parent = R * p_local + t</pre>
 */
public final class CoreMath {
    private static final double EPSILON = 1.0e-9;

    private CoreMath() {
    }

    public static final class Vec3 {
        public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
        public static final Vec3 X = new Vec3(1.0, 0.0, 0.0);
        public static final Vec3 Y = new Vec3(0.0, 1.0, 0.0);
        public static final Vec3 Z = new Vec3(0.0, 0.0, 1.0);

        public final double x;
        public final double y;
        public final double z;

        public Vec3(double x, double y, double z) {
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireFinite(z, "z");
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Vec3 add(Vec3 other) {
            Objects.requireNonNull(other, "other");
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        public Vec3 subtract(Vec3 other) {
            Objects.requireNonNull(other, "other");
            return new Vec3(x - other.x, y - other.y, z - other.z);
        }

        public Vec3 scale(double factor) {
            requireFinite(factor, "factor");
            return new Vec3(x * factor, y * factor, z * factor);
        }

        public double dot(Vec3 other) {
            Objects.requireNonNull(other, "other");
            return x * other.x + y * other.y + z * other.z;
        }

        public Vec3 cross(Vec3 other) {
            Objects.requireNonNull(other, "other");
            return new Vec3(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }

        public double squaredNorm() {
            return dot(this);
        }

        public double norm() {
            return Math.sqrt(squaredNorm());
        }

        public double distanceTo(Vec3 other) {
            return subtract(other).norm();
        }

        public Vec3 normalized() {
            double length = norm();
            if (length < EPSILON) {
                throw new IllegalStateException("Cannot normalize a zero-length vector");
            }
            return scale(1.0 / length);
        }

        public Vec3 normalizedOr(Vec3 fallback) {
            Objects.requireNonNull(fallback, "fallback");
            double length = norm();
            return length < EPSILON ? fallback.normalized() : scale(1.0 / length);
        }

        public Vec3 alignedWith(Vec3 reference) {
            Objects.requireNonNull(reference, "reference");
            return dot(reference) < 0.0 ? scale(-1.0) : this;
        }

        public boolean nearlyEquals(Vec3 other, double tolerance) {
            Objects.requireNonNull(other, "other");
            if (tolerance < 0.0 || !Double.isFinite(tolerance)) {
                throw new IllegalArgumentException("tolerance must be finite and non-negative");
            }
            return distanceTo(other) <= tolerance;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Vec3)) return false;
            Vec3 other = (Vec3) value;
            return Double.compare(x, other.x) == 0
                    && Double.compare(y, other.y) == 0
                    && Double.compare(z, other.z) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }

        @Override
        public String toString() {
            return "Vec3{" + x + ", " + y + ", " + z + '}';
        }
    }

    public static final class RigidPose {
        private final double[] rotation;
        private final Vec3 translation;

        public RigidPose(double[] rotationRowMajor, Vec3 translation) {
            Objects.requireNonNull(rotationRowMajor, "rotationRowMajor");
            Objects.requireNonNull(translation, "translation");
            if (rotationRowMajor.length != 9) {
                throw new IllegalArgumentException("rotation must contain exactly 9 values");
            }
            this.rotation = Arrays.copyOf(rotationRowMajor, rotationRowMajor.length);
            for (int index = 0; index < this.rotation.length; index++) {
                requireFinite(this.rotation[index], "rotation[" + index + "]");
            }
            validateRotation(this.rotation);
            this.translation = translation;
        }

        public static RigidPose identity() {
            return new RigidPose(new double[]{
                    1.0, 0.0, 0.0,
                    0.0, 1.0, 0.0,
                    0.0, 0.0, 1.0
            }, Vec3.ZERO);
        }

        public static RigidPose translation(Vec3 translation) {
            return new RigidPose(new double[]{
                    1.0, 0.0, 0.0,
                    0.0, 1.0, 0.0,
                    0.0, 0.0, 1.0
            }, translation);
        }

        public Vec3 transformPoint(Vec3 localPoint) {
            Objects.requireNonNull(localPoint, "localPoint");
            return rotateVector(localPoint).add(translation);
        }

        public Vec3 rotateVector(Vec3 localVector) {
            Objects.requireNonNull(localVector, "localVector");
            return new Vec3(
                    rotation[0] * localVector.x + rotation[1] * localVector.y + rotation[2] * localVector.z,
                    rotation[3] * localVector.x + rotation[4] * localVector.y + rotation[5] * localVector.z,
                    rotation[6] * localVector.x + rotation[7] * localVector.y + rotation[8] * localVector.z
            );
        }

        /** Returns {@code this * child}, applying child first and this pose second. */
        public RigidPose compose(RigidPose child) {
            Objects.requireNonNull(child, "child");
            double[] resultRotation = multiply3x3(rotation, child.rotation);
            Vec3 resultTranslation = transformPoint(child.translation);
            return new RigidPose(resultRotation, resultTranslation);
        }

        public RigidPose inverse() {
            double[] transpose = new double[]{
                    rotation[0], rotation[3], rotation[6],
                    rotation[1], rotation[4], rotation[7],
                    rotation[2], rotation[5], rotation[8]
            };
            Vec3 inverseTranslation = multiply(transpose, translation).scale(-1.0);
            return new RigidPose(transpose, inverseTranslation);
        }

        public double[] rotationRowMajor() {
            return Arrays.copyOf(rotation, rotation.length);
        }

        public Vec3 translation() {
            return translation;
        }

        @Override
        public String toString() {
            return "RigidPose{rotation=" + Arrays.toString(rotation)
                    + ", translation=" + translation + '}';
        }
    }

    public static double clamp01(double value) {
        requireFinite(value, "value");
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Vec3 multiply(double[] matrix, Vec3 vector) {
        return new Vec3(
                matrix[0] * vector.x + matrix[1] * vector.y + matrix[2] * vector.z,
                matrix[3] * vector.x + matrix[4] * vector.y + matrix[5] * vector.z,
                matrix[6] * vector.x + matrix[7] * vector.y + matrix[8] * vector.z
        );
    }

    private static double[] multiply3x3(double[] left, double[] right) {
        double[] result = new double[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                double value = 0.0;
                for (int k = 0; k < 3; k++) {
                    value += left[row * 3 + k] * right[k * 3 + column];
                }
                result[row * 3 + column] = value;
            }
        }
        return result;
    }

    private static void validateRotation(double[] matrix) {
        Vec3 row0 = new Vec3(matrix[0], matrix[1], matrix[2]);
        Vec3 row1 = new Vec3(matrix[3], matrix[4], matrix[5]);
        Vec3 row2 = new Vec3(matrix[6], matrix[7], matrix[8]);
        double tolerance = 1.0e-5;
        if (Math.abs(row0.norm() - 1.0) > tolerance
                || Math.abs(row1.norm() - 1.0) > tolerance
                || Math.abs(row2.norm() - 1.0) > tolerance
                || Math.abs(row0.dot(row1)) > tolerance
                || Math.abs(row0.dot(row2)) > tolerance
                || Math.abs(row1.dot(row2)) > tolerance) {
            throw new IllegalArgumentException("rotation must be orthonormal");
        }
        double determinant = row0.dot(row1.cross(row2));
        if (Math.abs(determinant - 1.0) > tolerance) {
            throw new IllegalArgumentException("rotation determinant must be +1");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
