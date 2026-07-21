package cl.skm.pulleyai;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Binds the installed APK, Android device, captured frames and staged runtime evidence. */
public final class CampaignEvidenceManifestBuilder {
    private CampaignEvidenceManifestBuilder() {}

    public static CampaignEvidenceManifestCore.Result build(
            Context context, CaptureStore store, String sessionId,
            List<RuntimeEvidenceTransactionCore.Entry> runtimeEntries) throws Exception {
        CaptureStore.Session session = store.getSession(sessionId);
        if (session == null) throw new IllegalStateException("session missing");
        List<CampaignEvidenceManifestCore.Item> items =
                new ArrayList<CampaignEvidenceManifestCore.Item>();
        for (CaptureStore.Frame frame : store.frames(sessionId)) {
            File file = new File(frame.filePath);
            if (!file.isFile()) continue;
            String hash = frame.sha256 != null && frame.sha256.matches("[0-9a-fA-F]{64}")
                    ? frame.sha256 : sha256(file);
            items.add(new CampaignEvidenceManifestCore.Item(
                    "FRAME", "frames/frame_" + String.format(java.util.Locale.ROOT, "%04d", frame.sequence) + ".jpg",
                    file.length(), hash));
        }
        if (runtimeEntries != null) {
            for (RuntimeEvidenceTransactionCore.Entry entry : runtimeEntries) {
                items.add(new CampaignEvidenceManifestCore.Item(
                        "RUNTIME", "runtime/" + entry.path, entry.sizeBytes, entry.sha256));
            }
        }
        File apk = new File(context.getApplicationInfo().sourceDir);
        String apkHash = apk.isFile() ? sha256(apk) : "";
        return CampaignEvidenceManifestCore.build(
                versionName(context), apkHash,
                Build.MANUFACTURER + " " + Build.MODEL, Build.VERSION.SDK_INT,
                session.id, session.code, session.ot, items);
    }

    private static String versionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception error) {
            return "";
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest()) out.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }
}