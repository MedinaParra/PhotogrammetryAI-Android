package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Dependency-free ORB-inspired feature core for portable ZIP reprocessing.
 * It uses a three-level pyramid, FAST-like corners, intensity-centroid orientation,
 * a deterministic 256-bit rotated binary descriptor, symmetric ratio matching,
 * and coverage measured against visually available cells.
 */
public final class MultiScaleOrientedFeatureCore {
    private static final double[] LEVEL_SCALES = {1.0, 0.80, 0.64};
    private static final int GRID_X = 8;
    private static final int GRID_Y = 6;
    private static final int MATCH_GRID_X = 6;
    private static final int MATCH_GRID_Y = 4;
    private static final int FAST_RADIUS = 3;
    private static final int ORIENTATION_RADIUS = 15;
    private static final int DESCRIPTOR_RADIUS = 15;
    private static final int FAST_THRESHOLD = 14;
    private static final int[][] CIRCLE = {
            {0,-3},{1,-3},{2,-2},{3,-1},{3,0},{3,1},{2,2},{1,3},
            {0,3},{-1,3},{-2,2},{-3,1},{-3,0},{-3,-1},{-2,-2},{-1,-3}
    };
    private static final int[][] PAIRS = buildPairs();

    private MultiScaleOrientedFeatureCore() {}

    public static FeatureSet detect(byte[] gray, int width, int height, int maxFeatures) {
        if (gray == null || width < 64 || height < 48 || gray.length < width * height) {
            return FeatureSet.empty(width, height);
        }
        int limit = Math.max(80, Math.min(2400, maxFeatures));
        List<Candidate> all = new ArrayList<Candidate>();
        for (int level = 0; level < LEVEL_SCALES.length; level++) {
            double scale = LEVEL_SCALES[level];
            int levelWidth = Math.max(48, (int) Math.round(width * scale));
            int levelHeight = Math.max(36, (int) Math.round(height * scale));
            byte[] levelGray = level == 0 ? gray : resizeBilinear(gray, width, height,
                    levelWidth, levelHeight);
            List<Candidate> candidates = detectLevel(levelGray, levelWidth, levelHeight,
                    level, scale);
            all.addAll(candidates);
        }
        Collections.sort(all, new Comparator<Candidate>() {
            @Override public int compare(Candidate a, Candidate b) {
                int compare = Double.compare(b.normalizedScore, a.normalizedScore);
                if (compare != 0) return compare;
                compare = Integer.compare(a.level, b.level);
                if (compare != 0) return compare;
                compare = Integer.compare(a.originalY, b.originalY);
                return compare != 0 ? compare : Integer.compare(a.originalX, b.originalX);
            }
        });

        int cells = GRID_X * GRID_Y;
        int cap = Math.max(2, (int) Math.ceil(limit / (double) cells));
        int[] perCell = new int[cells];
        int[] perLevel = new int[LEVEL_SCALES.length];
        int[] levelCaps = {Math.max(40, (int) Math.ceil(limit * 0.50)),
                Math.max(32, (int) Math.ceil(limit * 0.30)),
                Math.max(24, (int) Math.ceil(limit * 0.20))};
        List<Feature> features = new ArrayList<Feature>(limit);
        boolean[] used = new boolean[all.size()];

        for (int i = 0; i < all.size() && features.size() < limit; i++) {
            Candidate candidate = all.get(i);
            int cell = cell(candidate.originalX, candidate.originalY,
                    width, height, GRID_X, GRID_Y);
            if (perCell[cell] >= cap || perLevel[candidate.level] >= levelCaps[candidate.level]) continue;
            if (tooClose(features, candidate.originalX, candidate.originalY,
                    Math.max(5.0, 7.0 / candidate.scale))) continue;
            features.add(candidate.toFeature());
            used[i] = true;
            perCell[cell]++;
            perLevel[candidate.level]++;
        }
        for (int i = 0; i < all.size() && features.size() < limit; i++) {
            if (used[i]) continue;
            Candidate candidate = all.get(i);
            if (tooClose(features, candidate.originalX, candidate.originalY,
                    Math.max(4.0, 5.5 / candidate.scale))) continue;
            features.add(candidate.toFeature());
            int cell = cell(candidate.originalX, candidate.originalY,
                    width, height, GRID_X, GRID_Y);
            perCell[cell]++;
            perLevel[candidate.level]++;
        }
        int occupied = occupied(perCell);
        return new FeatureSet(width, height, features, occupied,
                occupied / (double) cells, perLevel);
    }

    public static MatchResult match(FeatureSet left, FeatureSet right) {
        if (left == null || right == null || left.features.isEmpty() || right.features.isEmpty()) {
            return MatchResult.empty("NO_FEATURES");
        }
        Best[] reverse = new Best[right.features.size()];
        for (int j = 0; j < right.features.size(); j++) {
            reverse[j] = best(right.features.get(j), left.features);
        }
        List<Double> ratios = new ArrayList<Double>();
        List<Observation> observations = new ArrayList<Observation>();
        int ratioPassed = 0;
        for (int i = 0; i < left.features.size(); i++) {
            Feature query = left.features.get(i);
            Best best = best(query, right.features);
            if (best.index < 0 || best.distance == Integer.MAX_VALUE) continue;
            double ratio = best.secondDistance == Integer.MAX_VALUE || best.secondDistance <= 0
                    ? 1.0 : best.distance / (double) best.secondDistance;
            ratios.add(ratio);
            if (best.distance > 86 || ratio > 0.82) continue;
            ratioPassed++;
            Best back = reverse[best.index];
            if (back == null || back.index != i) continue;
            Feature target = right.features.get(best.index);
            double scaleRatio = query.scale <= 0.0 ? 1.0 : target.scale / query.scale;
            if (scaleRatio < 0.42 || scaleRatio > 2.38) continue;
            double angleDelta = wrappedAngle(target.angleRad - query.angleRad);
            observations.add(new Observation(i, best.index,
                    query.x, query.y, target.x, target.y,
                    best.distance, ratio, scaleRatio, angleDelta));
        }
        Expansion expansion = guidedExpand(left, right, observations, ratios);
        observations = expansion.observations;
        if (expansion.affineSolved) ratioPassed = observations.size();
        Collections.sort(ratios);
        double p75Ratio = percentile(ratios, 0.75);
        double mutualFraction = ratioPassed == 0 ? 0.0
                : observations.size() / (double) ratioPassed;
        double coverage = relativeCoverage(left, right, observations);
        double orientationConcentration = orientationConcentration(observations);
        double medianScaleRatio = medianScaleRatio(observations);
        String status = observations.size() >= 36 && coverage >= 0.28
                && mutualFraction >= 0.28 ? "STRONG"
                : observations.size() >= 14 && coverage >= 0.10
                && mutualFraction >= 0.18 ? "USABLE" : "WEAK";
        return new MatchResult(observations, ratioPassed, p75Ratio,
                mutualFraction, coverage, orientationConcentration,
                medianScaleRatio, expansion.strictObservations,
                expansion.guidedAdded, expansion.affineSolved,
                expansion.affineInliers, expansion.affineInlierRatio,
                expansion.affineRmsPx, status);
    }

    private static List<Candidate> detectLevel(byte[] gray, int width, int height,
                                               int level, double scale) {
        List<RawCorner> raw = new ArrayList<RawCorner>();
        int border = DESCRIPTOR_RADIUS + 3;
        for (int y = border; y < height - border; y += 2) {
            for (int x = border; x < width - border; x += 2) {
                int score = fastScore(gray, width, x, y, FAST_THRESHOLD);
                if (score > 0) raw.add(new RawCorner(x, y, score));
            }
        }
        Collections.sort(raw, new Comparator<RawCorner>() {
            @Override public int compare(RawCorner a, RawCorner b) {
                return Integer.compare(b.score, a.score);
            }
        });
        List<RawCorner> selected = new ArrayList<RawCorner>();
        int maxLevelCandidates = Math.max(240, Math.min(3200, width * height / 80));
        for (RawCorner corner : raw) {
            if (selected.size() >= maxLevelCandidates) break;
            if (tooCloseRaw(selected, corner.x, corner.y, 4)) continue;
            selected.add(corner);
        }
        List<Candidate> out = new ArrayList<Candidate>(selected.size());
        for (RawCorner corner : selected) {
            double angle = orientation(gray, width, height, corner.x, corner.y);
            long[] descriptor = descriptor(gray, width, height,
                    corner.x, corner.y, angle);
            int ox = clamp((int) Math.round(corner.x / scale), 0,
                    Math.max(0, (int) Math.round(width / scale) - 1));
            int oy = clamp((int) Math.round(corner.y / scale), 0,
                    Math.max(0, (int) Math.round(height / scale) - 1));
            double normalized = corner.score * Math.max(0.35, scale * scale);
            out.add(new Candidate(ox, oy, corner.x, corner.y,
                    level, scale, angle, normalized,
                    descriptor[0], descriptor[1], descriptor[2], descriptor[3]));
        }
        return out;
    }

    private static int fastScore(byte[] gray, int width, int x, int y, int threshold) {
        int center = value(gray, width, x, y);
        int[] diff = new int[16];
        for (int i = 0; i < CIRCLE.length; i++) {
            int[] offset = CIRCLE[i];
            diff[i] = value(gray, width, x + offset[0], y + offset[1]) - center;
        }
        int bestBright = contiguousScore(diff, threshold, true);
        int bestDark = contiguousScore(diff, threshold, false);
        return Math.max(bestBright, bestDark);
    }

    private static int contiguousScore(int[] diff, int threshold, boolean bright) {
        int best = 0;
        for (int start = 0; start < 16; start++) {
            int count = 0;
            int score = 0;
            for (int k = 0; k < 16; k++) {
                int value = diff[(start + k) & 15];
                boolean pass = bright ? value >= threshold : value <= -threshold;
                if (!pass) break;
                count++;
                score += Math.abs(value);
            }
            if (count >= 9) best = Math.max(best, score + count * threshold);
        }
        return best;
    }

    private static double orientation(byte[] gray, int width, int height, int x, int y) {
        long m10 = 0L;
        long m01 = 0L;
        for (int oy = -ORIENTATION_RADIUS; oy <= ORIENTATION_RADIUS; oy++) {
            int maxX = (int) Math.floor(Math.sqrt(
                    ORIENTATION_RADIUS * ORIENTATION_RADIUS - oy * oy));
            for (int ox = -maxX; ox <= maxX; ox++) {
                int px = clamp(x + ox, 0, width - 1);
                int py = clamp(y + oy, 0, height - 1);
                int intensity = value(gray, width, px, py);
                m10 += (long) ox * intensity;
                m01 += (long) oy * intensity;
            }
        }
        return Math.atan2(m01, m10);
    }

    private static long[] descriptor(byte[] gray, int width, int height,
                                     int x, int y, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        long low = 0L;
        long high = 0L;
        long extraLow = 0L;
        long extraHigh = 0L;
        for (int i = 0; i < PAIRS.length; i++) {
            int[] pair = PAIRS[i];
            int ax = x + (int) Math.round(cos * pair[0] - sin * pair[1]);
            int ay = y + (int) Math.round(sin * pair[0] + cos * pair[1]);
            int bx = x + (int) Math.round(cos * pair[2] - sin * pair[3]);
            int by = y + (int) Math.round(sin * pair[2] + cos * pair[3]);
            ax = clamp(ax, 0, width - 1);
            ay = clamp(ay, 0, height - 1);
            bx = clamp(bx, 0, width - 1);
            by = clamp(by, 0, height - 1);
            int a = patchMean(gray, width, height, ax, ay);
            int b = patchMean(gray, width, height, bx, by);
            if (a < b) {
                if (i < 64) low |= 1L << i;
                else if (i < 128) high |= 1L << (i - 64);
                else if (i < 192) extraLow |= 1L << (i - 128);
                else extraHigh |= 1L << (i - 192);
            }
        }
        return new long[]{low, high, extraLow, extraHigh};
    }

    private static int[][] buildPairs() {
        int[][] pairs = new int[256][4];
        long state = 0x6a09e667f3bcc909L;
        for (int i = 0; i < pairs.length; i++) {
            for (int point = 0; point < 2; point++) {
                int x;
                int y;
                do {
                    state = xorshift(state);
                    x = gaussianCoordinate(state);
                    state = xorshift(state);
                    y = gaussianCoordinate(state);
                } while (x * x + y * y > DESCRIPTOR_RADIUS * DESCRIPTOR_RADIUS);
                pairs[i][point * 2] = x;
                pairs[i][point * 2 + 1] = y;
            }
        }
        return pairs;
    }

    private static long xorshift(long value) {
        value ^= value << 13;
        value ^= value >>> 7;
        value ^= value << 17;
        return value;
    }

    private static Expansion guidedExpand(FeatureSet left, FeatureSet right,
                                          List<Observation> strict,
                                          List<Double> ratios) {
        if (strict.size() < 6) return Expansion.unmodified(strict);
        List<AffineRansacCore.PointPair> pairs = new ArrayList<AffineRansacCore.PointPair>(strict.size());
        for (Observation observation : strict) {
            pairs.add(new AffineRansacCore.PointPair(observation.x, observation.y,
                    observation.u, observation.v));
        }
        double diagonal = Math.hypot(Math.max(left.width, right.width),
                Math.max(left.height, right.height));
        double seedThreshold = Math.max(18.0, diagonal * 0.025);
        AffineRansacCore.Result affine = AffineRansacCore.estimate(pairs,
                seedThreshold, Math.max(100, strict.size() * 5));
        if (!affine.solved || affine.model == null || affine.inliers.size() < 6) {
            return Expansion.unmodified(strict);
        }

        Set<Integer> inlierSet = new HashSet<Integer>(affine.inliers);
        List<Observation> seed = new ArrayList<Observation>(affine.inliers.size());
        for (int index = 0; index < strict.size(); index++) {
            if (inlierSet.contains(index)) seed.add(strict.get(index));
        }
        if (seed.size() < 6) return Expansion.unmodified(strict);
        Set<Integer> usedLeft = new HashSet<Integer>();
        Set<Integer> usedRight = new HashSet<Integer>();
        for (Observation observation : seed) {
            usedLeft.add(observation.leftIndex);
            usedRight.add(observation.rightIndex);
        }
        List<Observation> result = new ArrayList<Observation>(seed);
        double angleCenter = circularMean(seed);
        double angleConcentration = orientationConcentration(seed);
        double scaleCenter = medianScaleRatio(seed);
        double searchRadius = Math.max(38.0, diagonal * 0.060);
        double radius2 = searchRadius * searchRadius;
        List<GuidedCandidate> guided = new ArrayList<GuidedCandidate>();
        for (int i = 0; i < left.features.size(); i++) {
            if (usedLeft.contains(i)) continue;
            Feature query = left.features.get(i);
            double predictedX = affine.model.a * query.x + affine.model.b * query.y + affine.model.tx;
            double predictedY = affine.model.c * query.x + affine.model.d * query.y + affine.model.ty;
            int bestIndex = -1;
            int bestDistance = Integer.MAX_VALUE;
            int secondDistance = Integer.MAX_VALUE;
            double bestResidual = Double.POSITIVE_INFINITY;
            for (int j = 0; j < right.features.size(); j++) {
                if (usedRight.contains(j)) continue;
                Feature candidate = right.features.get(j);
                double dx = candidate.x - predictedX;
                double dy = candidate.y - predictedY;
                double residual2 = dx * dx + dy * dy;
                if (residual2 > radius2 * 0.58) continue;
                double scaleRatio = query.scale <= 0.0 ? 1.0 : candidate.scale / query.scale;
                if (scaleRatio < scaleCenter / 1.75 || scaleRatio > scaleCenter * 1.75) continue;
                double delta = wrappedAngle(candidate.angleRad - query.angleRad);
                if (angleConcentration >= 0.20
                        && Math.abs(wrappedAngle(delta - angleCenter)) > 1.05) continue;
                int distance = descriptorDistance(query, candidate);
                if (distance < bestDistance) {
                    secondDistance = bestDistance;
                    bestDistance = distance;
                    bestIndex = j;
                    bestResidual = Math.sqrt(residual2);
                } else if (distance < secondDistance) {
                    secondDistance = distance;
                }
            }
            if (bestIndex < 0 || bestDistance > 108) continue;
            double ratio = secondDistance == Integer.MAX_VALUE || secondDistance <= 0
                    ? 1.0 : bestDistance / (double) secondDistance;
            if (ratio > 0.90) continue;
            Feature target = right.features.get(bestIndex);
            double scaleRatio = query.scale <= 0.0 ? 1.0 : target.scale / query.scale;
            double angleDelta = wrappedAngle(target.angleRad - query.angleRad);
            Observation observation = new Observation(i, bestIndex, query.x, query.y,
                    target.x, target.y, bestDistance, ratio, scaleRatio, angleDelta);
            guided.add(new GuidedCandidate(observation,
                    bestDistance + 0.22 * bestResidual + 18.0 * ratio));
        }
        Collections.sort(guided, new Comparator<GuidedCandidate>() {
            @Override public int compare(GuidedCandidate a, GuidedCandidate b) {
                return Double.compare(a.quality, b.quality);
            }
        });
        int maxAdded = Math.min(180, Math.max(24, seed.size() * 2));
        int added = 0;
        for (GuidedCandidate candidate : guided) {
            Observation observation = candidate.observation;
            if (usedLeft.contains(observation.leftIndex)
                    || usedRight.contains(observation.rightIndex)) continue;
            result.add(observation);
            ratios.add(observation.secondBestRatio);
            usedLeft.add(observation.leftIndex);
            usedRight.add(observation.rightIndex);
            added++;
            if (added >= maxAdded) break;
        }
        return new Expansion(result, strict.size(), added, true,
                affine.inliers.size(), affine.inlierRatio, affine.rmsPx);
    }

    private static double circularMean(List<Observation> observations) {
        if (observations.isEmpty()) return 0.0;
        double sin = 0.0;
        double cos = 0.0;
        for (Observation observation : observations) {
            sin += Math.sin(observation.angleDeltaRad);
            cos += Math.cos(observation.angleDeltaRad);
        }
        return Math.atan2(sin, cos);
    }

    private static int descriptorDistance(Feature first, Feature second) {
        return Long.bitCount(first.descriptorLow ^ second.descriptorLow)
                + Long.bitCount(first.descriptorHigh ^ second.descriptorHigh)
                + Long.bitCount(first.descriptorExtraLow ^ second.descriptorExtraLow)
                + Long.bitCount(first.descriptorExtraHigh ^ second.descriptorExtraHigh);
    }

    private static Best best(Feature query, List<Feature> candidates) {
        int bestIndex = -1;
        int best = Integer.MAX_VALUE;
        int second = Integer.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            Feature candidate = candidates.get(i);
            double scaleRatio = query.scale <= 0.0 ? 1.0 : candidate.scale / query.scale;
            if (scaleRatio < 0.38 || scaleRatio > 2.62) continue;
            int distance = descriptorDistance(query, candidate);
            if (distance < best) {
                second = best;
                best = distance;
                bestIndex = i;
            } else if (distance < second) {
                second = distance;
            }
        }
        return new Best(bestIndex, best, second);
    }

    private static double relativeCoverage(FeatureSet left, FeatureSet right,
                                           List<Observation> observations) {
        if (observations.isEmpty()) return 0.0;
        boolean[] leftSupport = featureCells(left);
        boolean[] rightSupport = featureCells(right);
        boolean[] leftMatches = new boolean[MATCH_GRID_X * MATCH_GRID_Y];
        boolean[] rightMatches = new boolean[MATCH_GRID_X * MATCH_GRID_Y];
        for (Observation observation : observations) {
            leftMatches[cell(observation.x, observation.y, left.width, left.height,
                    MATCH_GRID_X, MATCH_GRID_Y)] = true;
            rightMatches[cell(observation.u, observation.v, right.width, right.height,
                    MATCH_GRID_X, MATCH_GRID_Y)] = true;
        }
        int available = Math.max(1, Math.min(count(leftSupport), count(rightSupport)));
        int covered = Math.min(count(leftMatches), count(rightMatches));
        return Math.max(0.0, Math.min(1.0, covered / (double) available));
    }

    private static boolean[] featureCells(FeatureSet set) {
        boolean[] cells = new boolean[MATCH_GRID_X * MATCH_GRID_Y];
        for (Feature feature : set.features) {
            cells[cell(feature.x, feature.y, set.width, set.height,
                    MATCH_GRID_X, MATCH_GRID_Y)] = true;
        }
        return cells;
    }

    private static double orientationConcentration(List<Observation> observations) {
        if (observations.isEmpty()) return 0.0;
        double sumSin = 0.0;
        double sumCos = 0.0;
        for (Observation observation : observations) {
            sumSin += Math.sin(observation.angleDeltaRad);
            sumCos += Math.cos(observation.angleDeltaRad);
        }
        return Math.hypot(sumSin, sumCos) / observations.size();
    }

    private static double medianScaleRatio(List<Observation> observations) {
        if (observations.isEmpty()) return 1.0;
        List<Double> values = new ArrayList<Double>(observations.size());
        for (Observation observation : observations) values.add(observation.scaleRatio);
        Collections.sort(values);
        int middle = values.size() / 2;
        return values.size() % 2 == 1 ? values.get(middle)
                : 0.5 * (values.get(middle - 1) + values.get(middle));
    }

    private static double percentile(List<Double> sorted, double q) {
        if (sorted == null || sorted.isEmpty()) return 1.0;
        double position = Math.max(0.0, Math.min(1.0, q)) * (sorted.size() - 1);
        int low = (int) Math.floor(position);
        int high = (int) Math.ceil(position);
        if (low == high) return sorted.get(low);
        double fraction = position - low;
        return sorted.get(low) * (1.0 - fraction) + sorted.get(high) * fraction;
    }

    private static int patchMean(byte[] gray, int width, int height, int x, int y) {
        int sum = 0;
        int count = 0;
        for (int oy = -1; oy <= 1; oy++) {
            int py = clamp(y + oy, 0, height - 1);
            for (int ox = -1; ox <= 1; ox++) {
                int px = clamp(x + ox, 0, width - 1);
                sum += value(gray, width, px, py);
                count++;
            }
        }
        return sum / Math.max(1, count);
    }

    private static int gaussianCoordinate(long state) {
        long a = Math.floorMod(state, 1000003L);
        state = xorshift(state);
        long b = Math.floorMod(state, 1000033L);
        state = xorshift(state);
        long c = Math.floorMod(state, 1000037L);
        double normalized = (a / 1000003.0 + b / 1000033.0 + c / 1000037.0) / 3.0;
        return (int) Math.round((normalized * 2.0 - 1.0) * DESCRIPTOR_RADIUS);
    }

    private static byte[] resizeBilinear(byte[] source, int sourceWidth, int sourceHeight,
                                         int targetWidth, int targetHeight) {
        byte[] target = new byte[targetWidth * targetHeight];
        double sx = sourceWidth / (double) targetWidth;
        double sy = sourceHeight / (double) targetHeight;
        for (int y = 0; y < targetHeight; y++) {
            double sourceY = (y + 0.5) * sy - 0.5;
            int y0 = clamp((int) Math.floor(sourceY), 0, sourceHeight - 1);
            int y1 = Math.min(sourceHeight - 1, y0 + 1);
            double fy = sourceY - Math.floor(sourceY);
            for (int x = 0; x < targetWidth; x++) {
                double sourceX = (x + 0.5) * sx - 0.5;
                int x0 = clamp((int) Math.floor(sourceX), 0, sourceWidth - 1);
                int x1 = Math.min(sourceWidth - 1, x0 + 1);
                double fx = sourceX - Math.floor(sourceX);
                double top = value(source, sourceWidth, x0, y0) * (1.0 - fx)
                        + value(source, sourceWidth, x1, y0) * fx;
                double bottom = value(source, sourceWidth, x0, y1) * (1.0 - fx)
                        + value(source, sourceWidth, x1, y1) * fx;
                target[y * targetWidth + x] = (byte) Math.round(
                        top * (1.0 - fy) + bottom * fy);
            }
        }
        return target;
    }

    private static boolean tooClose(List<Feature> features, double x, double y, double radius) {
        double squared = radius * radius;
        for (Feature feature : features) {
            double dx = feature.x - x;
            double dy = feature.y - y;
            if (dx * dx + dy * dy < squared) return true;
        }
        return false;
    }

    private static boolean tooCloseRaw(List<RawCorner> corners, int x, int y, int radius) {
        int squared = radius * radius;
        for (RawCorner corner : corners) {
            int dx = corner.x - x;
            int dy = corner.y - y;
            if (dx * dx + dy * dy < squared) return true;
        }
        return false;
    }

    private static int cell(double x, double y, int width, int height,
                            int columns, int rows) {
        int column = Math.min(columns - 1, Math.max(0,
                (int) Math.floor(x * columns / Math.max(1.0, width))));
        int row = Math.min(rows - 1, Math.max(0,
                (int) Math.floor(y * rows / Math.max(1.0, height))));
        return row * columns + column;
    }

    private static int occupied(int[] counts) {
        int result = 0;
        for (int value : counts) if (value > 0) result++;
        return result;
    }

    private static int count(boolean[] values) {
        int result = 0;
        for (boolean value : values) if (value) result++;
        return result;
    }

    private static int value(byte[] gray, int width, int x, int y) {
        return gray[y * width + x] & 0xff;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double wrappedAngle(double angle) {
        while (angle > Math.PI) angle -= 2.0 * Math.PI;
        while (angle < -Math.PI) angle += 2.0 * Math.PI;
        return angle;
    }

    private static final class GuidedCandidate {
        final Observation observation;
        final double quality;
        GuidedCandidate(Observation observation, double quality) {
            this.observation = observation;
            this.quality = quality;
        }
    }

    private static final class Expansion {
        final List<Observation> observations;
        final int strictObservations, guidedAdded;
        final boolean affineSolved;
        final int affineInliers;
        final double affineInlierRatio, affineRmsPx;
        Expansion(List<Observation> observations, int strictObservations,
                  int guidedAdded, boolean affineSolved, int affineInliers,
                  double affineInlierRatio, double affineRmsPx) {
            this.observations = observations;
            this.strictObservations = strictObservations;
            this.guidedAdded = guidedAdded;
            this.affineSolved = affineSolved;
            this.affineInliers = affineInliers;
            this.affineInlierRatio = affineInlierRatio;
            this.affineRmsPx = affineRmsPx;
        }
        static Expansion unmodified(List<Observation> observations) {
            return new Expansion(observations, observations.size(), 0,
                    false, 0, 0.0, Double.POSITIVE_INFINITY);
        }
    }

    private static final class RawCorner {
        final int x, y, score;
        RawCorner(int x, int y, int score) {
            this.x = x;
            this.y = y;
            this.score = score;
        }
    }

    private static final class Candidate {
        final int originalX, originalY, levelX, levelY, level;
        final double scale, angleRad, normalizedScore;
        final long descriptorLow, descriptorHigh, descriptorExtraLow, descriptorExtraHigh;
        Candidate(int originalX, int originalY, int levelX, int levelY,
                  int level, double scale, double angleRad,
                  double normalizedScore, long descriptorLow, long descriptorHigh,
                  long descriptorExtraLow, long descriptorExtraHigh) {
            this.originalX = originalX;
            this.originalY = originalY;
            this.levelX = levelX;
            this.levelY = levelY;
            this.level = level;
            this.scale = scale;
            this.angleRad = angleRad;
            this.normalizedScore = normalizedScore;
            this.descriptorLow = descriptorLow;
            this.descriptorHigh = descriptorHigh;
            this.descriptorExtraLow = descriptorExtraLow;
            this.descriptorExtraHigh = descriptorExtraHigh;
        }
        Feature toFeature() {
            return new Feature(originalX, originalY, level, scale, angleRad,
                    normalizedScore, descriptorLow, descriptorHigh,
                    descriptorExtraLow, descriptorExtraHigh);
        }
    }

    private static final class Best {
        final int index, distance, secondDistance;
        Best(int index, int distance, int secondDistance) {
            this.index = index;
            this.distance = distance;
            this.secondDistance = secondDistance;
        }
    }

    public static final class Feature {
        public final double x, y;
        public final int level;
        public final double scale, angleRad, response;
        public final long descriptorLow, descriptorHigh, descriptorExtraLow, descriptorExtraHigh;
        Feature(double x, double y, int level, double scale, double angleRad,
                double response, long descriptorLow, long descriptorHigh,
                long descriptorExtraLow, long descriptorExtraHigh) {
            this.x = x;
            this.y = y;
            this.level = level;
            this.scale = scale;
            this.angleRad = angleRad;
            this.response = response;
            this.descriptorLow = descriptorLow;
            this.descriptorHigh = descriptorHigh;
            this.descriptorExtraLow = descriptorExtraLow;
            this.descriptorExtraHigh = descriptorExtraHigh;
        }
    }

    public static final class FeatureSet {
        public final int width, height;
        public final List<Feature> features;
        public final int occupiedGridCells;
        public final double spatialCoverage;
        public final int[] featuresPerLevel;
        FeatureSet(int width, int height, List<Feature> features,
                   int occupiedGridCells, double spatialCoverage, int[] featuresPerLevel) {
            this.width = width;
            this.height = height;
            this.features = Collections.unmodifiableList(new ArrayList<Feature>(features));
            this.occupiedGridCells = occupiedGridCells;
            this.spatialCoverage = spatialCoverage;
            this.featuresPerLevel = featuresPerLevel.clone();
        }
        static FeatureSet empty(int width, int height) {
            return new FeatureSet(width, height, Collections.<Feature>emptyList(),
                    0, 0.0, new int[LEVEL_SCALES.length]);
        }
    }

    public static final class Observation {
        public final int leftIndex, rightIndex;
        public final double x, y, u, v;
        public final int descriptorDistance;
        public final double secondBestRatio, scaleRatio, angleDeltaRad;
        Observation(int leftIndex, int rightIndex,
                    double x, double y, double u, double v,
                    int descriptorDistance, double secondBestRatio,
                    double scaleRatio, double angleDeltaRad) {
            this.leftIndex = leftIndex;
            this.rightIndex = rightIndex;
            this.x = x;
            this.y = y;
            this.u = u;
            this.v = v;
            this.descriptorDistance = descriptorDistance;
            this.secondBestRatio = secondBestRatio;
            this.scaleRatio = scaleRatio;
            this.angleDeltaRad = angleDeltaRad;
        }
    }

    public static final class MatchResult {
        public final List<Observation> observations;
        public final int ratioPassed;
        public final double p75SecondBestRatio, mutualFraction, roiCoverage;
        public final double orientationConcentration, medianScaleRatio;
        public final int strictObservations, guidedAdded;
        public final boolean affineSolved;
        public final int affineInliers;
        public final double affineInlierRatio, affineRmsPx;
        public final String status;
        MatchResult(List<Observation> observations, int ratioPassed,
                    double p75SecondBestRatio, double mutualFraction,
                    double roiCoverage, double orientationConcentration,
                    double medianScaleRatio, int strictObservations,
                    int guidedAdded, boolean affineSolved, int affineInliers,
                    double affineInlierRatio, double affineRmsPx, String status) {
            this.observations = Collections.unmodifiableList(
                    new ArrayList<Observation>(observations));
            this.ratioPassed = ratioPassed;
            this.p75SecondBestRatio = p75SecondBestRatio;
            this.mutualFraction = mutualFraction;
            this.roiCoverage = roiCoverage;
            this.orientationConcentration = orientationConcentration;
            this.medianScaleRatio = medianScaleRatio;
            this.strictObservations = strictObservations;
            this.guidedAdded = guidedAdded;
            this.affineSolved = affineSolved;
            this.affineInliers = affineInliers;
            this.affineInlierRatio = affineInlierRatio;
            this.affineRmsPx = affineRmsPx;
            this.status = status;
        }
        static MatchResult empty(String status) {
            return new MatchResult(Collections.<Observation>emptyList(), 0,
                    1.0, 0.0, 0.0, 0.0, 1.0,
                    0, 0, false, 0, 0.0,
                    Double.POSITIVE_INFINITY, status);
        }
    }
}
