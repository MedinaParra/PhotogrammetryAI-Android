package cl.skm.pulleyai;

/** Pure deterministic angular coverage model used by capture UI and tests. */
public final class CoveragePlanner {
    public static final int SECTOR_COUNT = 12;
    public static final double SECTOR_DEGREES = 360.0 / SECTOR_COUNT;
    public static final int COMPLETE_MASK = (1 << SECTOR_COUNT) - 1;

    private CoveragePlanner() {
    }

    public static double normalizeYaw(double yawDegrees) {
        double value = yawDegrees % 360.0;
        return value < 0.0 ? value + 360.0 : value;
    }

    public static int sectorForYaw(double yawDegrees) {
        int sector = (int) Math.floor(normalizeYaw(yawDegrees) / SECTOR_DEGREES);
        return Math.max(0, Math.min(SECTOR_COUNT - 1, sector));
    }

    public static int addSector(int mask, int sector) {
        if (sector < 0 || sector >= SECTOR_COUNT) {
            throw new IllegalArgumentException("sector out of range: " + sector);
        }
        return mask | (1 << sector);
    }

    public static boolean contains(int mask, int sector) {
        return sector >= 0 && sector < SECTOR_COUNT && (mask & (1 << sector)) != 0;
    }

    public static int coveredCount(int mask) {
        return Integer.bitCount(mask & COMPLETE_MASK);
    }

    public static boolean isComplete(int mask) {
        return (mask & COMPLETE_MASK) == COMPLETE_MASK;
    }

    /** Returns the nearest missing sector, preferring forward movement around the object. */
    public static int nextMissingSector(int mask, int currentSector) {
        if (isComplete(mask)) return -1;
        int start = Math.max(0, Math.min(SECTOR_COUNT - 1, currentSector));
        for (int offset = 1; offset <= SECTOR_COUNT; offset++) {
            int candidate = (start + offset) % SECTOR_COUNT;
            if (!contains(mask, candidate)) return candidate;
        }
        return -1;
    }

    public static String sectorLabel(int sector) {
        if (sector < 0) return "completo";
        int start = (int) Math.round(sector * SECTOR_DEGREES);
        int end = (int) Math.round((sector + 1) * SECTOR_DEGREES);
        return start + "°–" + end + "°";
    }
}
