package cl.skm.pulleyai;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

/** Atomic session-local persistence for the operator-selected pulley target lock. */
public final class PulleyTargetLockStore {
    private static final String SIGNATURE = "target_lock_signature.txt";
    private static final String REFERENCE = "target_lock_reference.jpg";

    private PulleyTargetLockStore() {}

    public static boolean has(File sessionDir) {
        return read(sessionDir) != null && new File(sessionDir, REFERENCE).isFile();
    }

    public static PulleyTargetLockCore.Signature read(File sessionDir) {
        if (sessionDir == null) return null;
        File file = new File(sessionDir, SIGNATURE);
        if (!file.isFile()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return PulleyTargetLockCore.decode(reader.readLine());
        } catch (Exception error) {
            return null;
        }
    }

    public static void save(File sessionDir, PulleyTargetLockCore.Signature signature,
                            byte[] referenceJpeg) throws Exception {
        if (sessionDir == null || signature == null || !signature.valid)
            throw new IllegalArgumentException("Firma de objetivo inválida");
        if (referenceJpeg == null || referenceJpeg.length == 0)
            throw new IllegalArgumentException("Referencia visual vacía");
        if (!sessionDir.isDirectory() && !sessionDir.mkdirs())
            throw new IllegalStateException("No se pudo crear carpeta de sesión");
        File signatureTmp = new File(sessionDir, SIGNATURE + ".tmp");
        File referenceTmp = new File(sessionDir, REFERENCE + ".tmp");
        writeSynced(signatureTmp, (PulleyTargetLockCore.encode(signature) + "\n").getBytes(StandardCharsets.UTF_8));
        writeSynced(referenceTmp, referenceJpeg);
        File signatureFile = new File(sessionDir, SIGNATURE);
        File referenceFile = new File(sessionDir, REFERENCE);
        replace(signatureTmp, signatureFile);
        replace(referenceTmp, referenceFile);
    }

    public static void clear(File sessionDir) {
        if (sessionDir == null) return;
        new File(sessionDir, SIGNATURE).delete();
        new File(sessionDir, REFERENCE).delete();
        new File(sessionDir, SIGNATURE + ".tmp").delete();
        new File(sessionDir, REFERENCE + ".tmp").delete();
    }

    private static void writeSynced(File file, byte[] bytes) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private static void replace(File source, File target) {
        if (target.exists() && !target.delete())
            throw new IllegalStateException("No se pudo reemplazar " + target.getName());
        if (!source.renameTo(target))
            throw new IllegalStateException("No se pudo publicar " + target.getName());
    }
}
