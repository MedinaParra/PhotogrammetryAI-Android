package cl.ingenieria.photogrammetryai.core.foundation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Versioned, dependency-free wire protocol between the Android/JVM core and the OCCT JNI adapter.
 *
 * <p>The protocol is tab separated, one record per line. Text fields use percent encoding so STEP
 * names may safely contain spaces, tabs or non-ASCII characters without adding a JSON dependency
 * to the structural core.</p>
 */
public final class StepImportWireCodec {
    public static final String VERSION = "PGAI_STEP_V1";

    private StepImportWireCodec() {
    }

    public static final class NativeFeature {
        private final String id;
        private final String name;
        private final String type;
        private final CoreMath.Vec3 origin;
        private final CoreMath.Vec3 direction;
        private final double radiusMm;
        private final double extentMm;
        private final double trackingWeight;

        NativeFeature(
                String id,
                String name,
                String type,
                CoreMath.Vec3 origin,
                CoreMath.Vec3 direction,
                double radiusMm,
                double extentMm,
                double trackingWeight
        ) {
            this.id = requireText(id, "id");
            this.name = requireText(name, "name");
            this.type = requireText(type, "type");
            this.origin = origin;
            this.direction = direction;
            this.radiusMm = requireFinite(radiusMm, "radiusMm");
            this.extentMm = requireFinite(extentMm, "extentMm");
            this.trackingWeight = requireFinite(trackingWeight, "trackingWeight");
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public String type() {
            return type;
        }

        public CoreMath.Vec3 origin() {
            return origin;
        }

        public CoreMath.Vec3 direction() {
            return direction;
        }

        public double radiusMm() {
            return radiusMm;
        }

        public double extentMm() {
            return extentMm;
        }

        public double trackingWeight() {
            return trackingWeight;
        }
    }

    public static final class NativePayload {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final String sourceFileName;
        private final String renderMeshKey;
        private final List<NativeFeature> features;

        NativePayload(
                boolean success,
                String errorCode,
                String message,
                String sourceFileName,
                String renderMeshKey,
                List<NativeFeature> features
        ) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = requireText(message, "message");
            this.sourceFileName = sourceFileName == null ? "" : sourceFileName;
            this.renderMeshKey = renderMeshKey == null ? "" : renderMeshKey;
            this.features = Collections.unmodifiableList(new ArrayList<>(features));
        }

        public boolean success() {
            return success;
        }

        public String errorCode() {
            return errorCode;
        }

        public String message() {
            return message;
        }

        public String sourceFileName() {
            return sourceFileName;
        }

        public String renderMeshKey() {
            return renderMeshKey;
        }

        public List<NativeFeature> features() {
            return features;
        }
    }

    public static NativePayload decode(String wireText) {
        if (wireText == null || wireText.trim().isEmpty()) {
            throw new IllegalArgumentException("Native STEP payload is empty");
        }
        String[] lines = wireText.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length < 2 || !VERSION.equals(lines[0])) {
            throw new IllegalArgumentException("Unsupported STEP payload version");
        }

        String[] status = lines[1].split("\t", -1);
        if (status.length != 6 || !"STATUS".equals(status[0])) {
            throw new IllegalArgumentException("Malformed STEP status record");
        }

        boolean success;
        if ("OK".equals(status[1])) {
            success = true;
        } else if ("ERROR".equals(status[1])) {
            success = false;
        } else {
            throw new IllegalArgumentException("Unknown STEP status " + status[1]);
        }

        String errorCode = decodeField(status[2]);
        String message = decodeField(status[3]);
        String sourceFileName = decodeField(status[4]);
        String renderMeshKey = decodeField(status[5]);
        List<NativeFeature> features = new ArrayList<>();

        for (int lineIndex = 2; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length != 13 || !"FEATURE".equals(fields[0])) {
                throw new IllegalArgumentException("Malformed STEP feature at line " + (lineIndex + 1));
            }
            features.add(new NativeFeature(
                    decodeField(fields[1]),
                    decodeField(fields[2]),
                    fields[3],
                    new CoreMath.Vec3(parse(fields[4]), parse(fields[5]), parse(fields[6])),
                    new CoreMath.Vec3(parse(fields[7]), parse(fields[8]), parse(fields[9])),
                    parse(fields[10]),
                    parse(fields[11]),
                    parse(fields[12])
            ));
        }

        if (!success && (errorCode == null || errorCode.trim().isEmpty())) {
            throw new IllegalArgumentException("Native STEP failure has no error code");
        }
        return new NativePayload(success, emptyToNull(errorCode), message, sourceFileName,
                renderMeshKey, features);
    }

    /** Kept public so native test fixtures can generate exactly the same escaping. */
    public static String encodeField(String value) {
        if (value == null) {
            return "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte raw : bytes) {
            int valueByte = raw & 0xff;
            if ((valueByte >= 'a' && valueByte <= 'z')
                    || (valueByte >= 'A' && valueByte <= 'Z')
                    || (valueByte >= '0' && valueByte <= '9')
                    || valueByte == '-' || valueByte == '_' || valueByte == '.' || valueByte == '~') {
                encoded.append((char) valueByte);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit((valueByte >>> 4) & 0xf, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(valueByte & 0xf, 16)));
            }
        }
        return encoded.toString();
    }

    public static String decodeField(String encoded) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(encoded.length());
        for (int index = 0; index < encoded.length(); index++) {
            char current = encoded.charAt(index);
            if (current == '%') {
                if (index + 2 >= encoded.length()) {
                    throw new IllegalArgumentException("Incomplete percent encoding");
                }
                int high = Character.digit(encoded.charAt(index + 1), 16);
                int low = Character.digit(encoded.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IllegalArgumentException("Invalid percent encoding");
                }
                bytes.write((high << 4) | low);
                index += 2;
            } else {
                if (current > 0x7f) {
                    byte[] direct = String.valueOf(current).getBytes(StandardCharsets.UTF_8);
                    bytes.write(direct, 0, direct.length);
                } else {
                    bytes.write((byte) current);
                }
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static double parse(String value) {
        try {
            return requireFinite(Double.parseDouble(value), "numeric field");
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid STEP numeric field: " + value, error);
        }
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
