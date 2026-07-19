package cl.ingenieria.photogrammetryai.core.foundation;

import cl.ingenieria.photogrammetryai.core.foundation.CorePorts.ImportRequest;
import cl.ingenieria.photogrammetryai.core.foundation.CorePorts.ImportResult;
import cl.ingenieria.photogrammetryai.core.foundation.CorePorts.StepReferenceImporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/** Android-facing adapter that delegates STEP/B-Rep inspection to the optional OCCT JNI library. */
public final class NativeStepReferenceImporter implements StepReferenceImporter {
    public interface NativeBridge {
        String importStepEncoded(String localFilePath, String displayName);
    }

    private static final LibraryState LIBRARY_STATE = loadNativeLibrary();

    private final NativeBridge nativeBridge;
    private final StepReferenceImportMapper mapper;

    public NativeStepReferenceImporter() {
        this(new JniBridge(), new StepReferenceImportMapper());
    }

    /** Constructor intended for deterministic JVM tests and alternative native transports. */
    public NativeStepReferenceImporter(NativeBridge nativeBridge, StepReferenceImportMapper mapper) {
        this.nativeBridge = Objects.requireNonNull(nativeBridge, "nativeBridge");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public static boolean isNativeLibraryAvailable() {
        return LIBRARY_STATE.loaded;
    }

    public static String nativeLibraryError() {
        return LIBRARY_STATE.errorMessage;
    }

    @Override
    public ImportResult importStep(ImportRequest request) {
        Objects.requireNonNull(request, "request");
        File source = new File(request.localFilePath());
        ImportResult validation = validateSource(source);
        if (validation != null) {
            return validation;
        }

        String sha256;
        try {
            sha256 = sha256(source);
        } catch (IOException error) {
            return ImportResult.failure("STEP_HASH_FAILED", "No se pudo leer el STEP: " + error.getMessage());
        }

        final String encoded;
        try {
            encoded = nativeBridge.importStepEncoded(source.getAbsolutePath(), request.displayName());
        } catch (UnsatisfiedLinkError error) {
            return ImportResult.failure(
                    "OCCT_MISSING",
                    "El núcleo OpenCASCADE no está disponible en este APK: " + safeMessage(error)
            );
        } catch (RuntimeException error) {
            return ImportResult.failure(
                    "NATIVE_STEP_EXCEPTION",
                    "Falló el adaptador STEP nativo: " + safeMessage(error)
            );
        }

        final StepImportWireCodec.NativePayload payload;
        try {
            payload = StepImportWireCodec.decode(encoded);
        } catch (IllegalArgumentException error) {
            return ImportResult.failure("INVALID_NATIVE_PAYLOAD", error.getMessage());
        }
        return mapper.map(payload, request, sha256);
    }

    private static ImportResult validateSource(File source) {
        if (!source.exists()) {
            return ImportResult.failure("STEP_NOT_FOUND", "No existe el archivo STEP seleccionado.");
        }
        if (!source.isFile() || !source.canRead()) {
            return ImportResult.failure("STEP_NOT_READABLE", "El archivo STEP no puede ser leído.");
        }
        String lowerName = source.getName().toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".step") && !lowerName.endsWith(".stp")) {
            return ImportResult.failure("UNSUPPORTED_CAD_FORMAT", "Solo se aceptan archivos .step o .stp.");
        }
        if (source.length() <= 0L) {
            return ImportResult.failure("STEP_EMPTY", "El archivo STEP está vacío.");
        }
        return null;
    }

    private static String sha256(File source) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
        byte[] buffer = new byte[64 * 1024];
        try (FileInputStream input = new FileInputStream(source)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }

    private static LibraryState loadNativeLibrary() {
        try {
            System.loadLibrary("photogrammetry_cadcore");
            return new LibraryState(true, "");
        } catch (UnsatisfiedLinkError error) {
            return new LibraryState(false, safeMessage(error));
        } catch (SecurityException error) {
            return new LibraryState(false, safeMessage(error));
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }

    private static native String nativeImportStepEncoded(String localFilePath, String displayName);

    private static final class JniBridge implements NativeBridge {
        @Override
        public String importStepEncoded(String localFilePath, String displayName) {
            if (!LIBRARY_STATE.loaded) {
                throw new UnsatisfiedLinkError(LIBRARY_STATE.errorMessage);
            }
            return nativeImportStepEncoded(localFilePath, displayName);
        }
    }

    private static final class LibraryState {
        private final boolean loaded;
        private final String errorMessage;

        private LibraryState(boolean loaded, String errorMessage) {
            this.loaded = loaded;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }
    }
}
