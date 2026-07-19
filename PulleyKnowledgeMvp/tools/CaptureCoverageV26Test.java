import cl.skm.pulleyai.CoveragePlanner;

public final class CaptureCoverageV26Test {
    public static void main(String[] args) {
        require(CoveragePlanner.sectorForYaw(0.0) == 0, "0 degrees");
        require(CoveragePlanner.sectorForYaw(29.999) == 0, "sector boundary low");
        require(CoveragePlanner.sectorForYaw(30.0) == 1, "sector boundary high");
        require(CoveragePlanner.sectorForYaw(-1.0) == 11, "negative yaw normalisation");
        require(CoveragePlanner.sectorForYaw(721.0) == 0, "multiple turns");
        int mask = 0;
        for (int sector = 0; sector < CoveragePlanner.SECTOR_COUNT; sector++) mask = CoveragePlanner.addSector(mask, sector);
        require(CoveragePlanner.isComplete(mask), "complete mask");
        require(CoveragePlanner.coveredCount(mask) == 12, "coverage count");
        require(CoveragePlanner.nextMissingSector(mask, 4) == -1, "complete has no next");
        int sparse = CoveragePlanner.addSector(0, 0);
        sparse = CoveragePlanner.addSector(sparse, 1);
        sparse = CoveragePlanner.addSector(sparse, 3);
        require(CoveragePlanner.nextMissingSector(sparse, 1) == 2, "prefer forward missing sector");
        require(CoveragePlanner.sectorLabel(2).equals("60°–90°"), "sector label");
        System.out.println("CaptureCoverageV26Test OK mask=" + mask);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
