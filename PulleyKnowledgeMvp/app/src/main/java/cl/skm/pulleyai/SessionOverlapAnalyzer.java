package cl.skm.pulleyai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Computes real image correspondences between adjacent accepted views and writes an auditable report. */
public final class SessionOverlapAnalyzer {
    private SessionOverlapAnalyzer() {
    }

    public static Report analyze(CaptureStore store, String sessionId) throws Exception {
        List<CaptureStore.Frame> all = store.frames(sessionId);
        List<CaptureStore.Frame> accepted = new ArrayList<CaptureStore.Frame>();
        for (CaptureStore.Frame frame : all) {
            if ("ACCEPTED".equals(frame.quality) && new File(frame.filePath).isFile()) accepted.add(frame);
        }
        List<Pair> pairs = new ArrayList<Pair>();
        int strong = 0;
        int usable = 0;
        for (int i = 1; i < accepted.size(); i++) {
            CaptureStore.Frame left = accepted.get(i - 1);
            CaptureStore.Frame right = accepted.get(i);
            if (!left.band.equals(right.band)) continue;
            int sectorGap = circularGap(left.sector, right.sector);
            if (sectorGap > 2) continue;
            Gray a = decode(left.filePath);
            Gray b = decode(right.filePath);
            VisualFeatureCore.FeatureSet featuresA = VisualFeatureCore.detect(a.pixels, a.width, a.height, 450);
            VisualFeatureCore.FeatureSet featuresB = VisualFeatureCore.detect(b.pixels, b.width, b.height, 450);
            VisualFeatureCore.PairResult match = VisualFeatureCore.match(featuresA, featuresB);
            int coherent = coherent(match);
            Pair pair = new Pair(left.sequence, right.sequence, left.band, left.sector, right.sector,
                    featuresA.features.size(), featuresB.features.size(), match.matches.size(), coherent,
                    match.medianDx, match.medianDy, match.status);
            pairs.add(pair);
            if ("STRONG".equals(match.status)) strong++;
            if ("STRONG".equals(match.status) || "USABLE".equals(match.status)) usable++;
        }
        int requiredPairs = Math.max(8, Math.min(20, accepted.size() / 3));
        boolean ready = usable >= requiredPairs && strong >= Math.max(3, requiredPairs / 3);
        Report report = new Report(accepted.size(), pairs, strong, usable, requiredPairs, ready);
        File output = new File(store.sessionDir(sessionId), "overlap_report.json");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(report.toJson().getBytes(StandardCharsets.UTF_8));
        }
        return report;
    }

    private static Gray decode(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / sample > 640) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) throw new IllegalStateException("No se pudo decodificar " + path);
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            byte[] gray = new byte[width * height];
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1);
                for (int x = 0; x < width; x++) {
                    int color = row[x];
                    gray[y * width + x] = (byte) Math.round(
                            0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color));
                }
            }
            return new Gray(width, height, gray);
        } finally {
            bitmap.recycle();
        }
    }

    private static int coherent(VisualFeatureCore.PairResult result) {
        int count = 0;
        for (VisualFeatureCore.Match match : result.matches) {
            if (Math.abs(match.dx - result.medianDx) <= 5.0
                    && Math.abs(match.dy - result.medianDy) <= 5.0) count++;
        }
        return count;
    }

    private static int circularGap(int a, int b) {
        int gap = Math.abs(a - b);
        return Math.min(gap, CoveragePlanner.SECTOR_COUNT - gap);
    }

    private static final class Gray {
        final int width;
        final int height;
        final byte[] pixels;
        Gray(int width, int height, byte[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }

    public static final class Pair {
        public final int leftSequence;
        public final int rightSequence;
        public final String band;
        public final int leftSector;
        public final int rightSector;
        public final int leftFeatures;
        public final int rightFeatures;
        public final int matches;
        public final int coherentMatches;
        public final double medianDx;
        public final double medianDy;
        public final String status;

        Pair(int leftSequence, int rightSequence, String band, int leftSector, int rightSector,
             int leftFeatures, int rightFeatures, int matches, int coherentMatches,
             double medianDx, double medianDy, String status) {
            this.leftSequence = leftSequence;
            this.rightSequence = rightSequence;
            this.band = band;
            this.leftSector = leftSector;
            this.rightSector = rightSector;
            this.leftFeatures = leftFeatures;
            this.rightFeatures = rightFeatures;
            this.matches = matches;
            this.coherentMatches = coherentMatches;
            this.medianDx = medianDx;
            this.medianDy = medianDy;
            this.status = status;
        }
    }

    public static final class Report {
        public final int acceptedFrames;
        public final List<Pair> pairs;
        public final int strongPairs;
        public final int usablePairs;
        public final int requiredPairs;
        public final boolean ready;

        Report(int acceptedFrames, List<Pair> pairs, int strongPairs, int usablePairs,
               int requiredPairs, boolean ready) {
            this.acceptedFrames = acceptedFrames;
            this.pairs = pairs;
            this.strongPairs = strongPairs;
            this.usablePairs = usablePairs;
            this.requiredPairs = requiredPairs;
            this.ready = ready;
        }

        public String summary() {
            return "Pares fuertes " + strongPairs + " · utilizables " + usablePairs + "/" + requiredPairs
                    + (ready ? " · SOLAPE APROBADO" : " · SOLAPE INSUFICIENTE");
        }

        String toJson() {
            StringBuilder json = new StringBuilder(2048 + pairs.size() * 260);
            json.append("{\n  \"schema\": \"skm-polea-overlap/1\",\n")
                    .append("  \"acceptedFrames\": ").append(acceptedFrames).append(",\n")
                    .append("  \"strongPairs\": ").append(strongPairs).append(",\n")
                    .append("  \"usablePairs\": ").append(usablePairs).append(",\n")
                    .append("  \"requiredPairs\": ").append(requiredPairs).append(",\n")
                    .append("  \"ready\": ").append(ready).append(",\n")
                    .append("  \"pairs\": [\n");
            for (int i = 0; i < pairs.size(); i++) {
                Pair p = pairs.get(i);
                json.append(String.format(Locale.ROOT,
                        "    {\"left\":%d,\"right\":%d,\"band\":\"%s\",\"sectors\":[%d,%d]," +
                                "\"features\":[%d,%d],\"matches\":%d,\"coherent\":%d," +
                                "\"medianShift\":[%.3f,%.3f],\"status\":\"%s\"}",
                        p.leftSequence, p.rightSequence, p.band, p.leftSector, p.rightSector,
                        p.leftFeatures, p.rightFeatures, p.matches, p.coherentMatches,
                        p.medianDx, p.medianDy, p.status));
                if (i + 1 < pairs.size()) json.append(',');
                json.append('\n');
            }
            json.append("  ]\n}");
            return json.toString();
        }
    }
}
