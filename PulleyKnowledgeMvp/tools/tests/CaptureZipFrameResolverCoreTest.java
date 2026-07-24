package cl.skm.pulleyai;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class CaptureZipFrameResolverCoreTest {
    public static void main(String[] args) {
        Set<String> entries = new HashSet<String>(Arrays.asList(
                "manifest.json",
                "frames/frame_0001.jpg",
                "FRAMES/FRAME_0003.JPG",
                "nested/frames/frame_4.jpg",
                "frames/frame_0005.jpeg"));

        check("frames/frame_0001.jpg".equals(
                CaptureZipFrameResolverCore.resolveEntryName(entries, 1)), "exact padded");
        check("FRAMES/FRAME_0003.JPG".equals(
                CaptureZipFrameResolverCore.resolveEntryName(entries, 3)), "case insensitive");
        check("nested/frames/frame_4.jpg".equals(
                CaptureZipFrameResolverCore.resolveEntryName(entries, 4)), "suffix fallback");
        check("frames/frame_0005.jpeg".equals(
                CaptureZipFrameResolverCore.resolveEntryName(entries, 5)), "jpeg extension");
        check(CaptureZipFrameResolverCore.resolveEntryName(entries, 99) == null,
                "missing frame");

        CaptureZipFrameResolverCore.Preflight preflight =
                new CaptureZipFrameResolverCore.Preflight(42, 36);
        preflight.entriesFound = 36;
        preflight.hashesVerified = 36;
        preflight.decodedFrames = 35;
        preflight.fail(18, "DECODE_FAILED");
        String summary = preflight.concise();
        check(summary.contains("manifest 42"), "manifest count");
        check(summary.contains("aceptadas 36"), "accepted count");
        check(summary.contains("decodificadas 35"), "decoded count");
        check(summary.contains("#18 DECODE_FAILED"), "failure reason");
        String json = preflight.canonicalJson();
        check(json.contains("skm-capture-zip-preflight/1"), "schema");
        check(json.contains("\"sequence\":18"), "json failure");
        System.out.println("CaptureZipFrameResolverCoreTest PASS");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
