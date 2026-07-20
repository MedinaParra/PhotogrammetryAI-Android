package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Propagates calibrated relative poses into a global camera frame and checks loop consistency. */
public final class GlobalPoseGraphCore {
    private GlobalPoseGraphCore() {}

    public static Result solve(int nodeCount, List<Edge> sourceEdges) {
        if (nodeCount <= 0) return Result.failed("NO_NODES");
        List<Edge> edges = new ArrayList<Edge>();
        if (sourceEdges != null) {
            for (Edge edge : sourceEdges) {
                if (edge != null && edge.valid(nodeCount)) edges.add(edge);
            }
        }
        List<List<Integer>> adjacency = new ArrayList<List<Integer>>();
        for (int i=0;i<nodeCount;i++) adjacency.add(new ArrayList<Integer>());
        for (int i=0;i<edges.size();i++) {
            Edge edge = edges.get(i);
            adjacency.get(edge.from).add(i);
            adjacency.get(edge.to).add(i);
        }

        Pose[] poses = new Pose[nodeCount];
        boolean[] visited = new boolean[nodeCount];
        boolean[] treeEdge = new boolean[edges.size()];
        poses[0] = new Pose(identity(), new double[]{0,0,0});
        visited[0] = true;
        PriorityQueue<Candidate> queue = new PriorityQueue<Candidate>(
                new Comparator<Candidate>() {
                    @Override public int compare(Candidate a, Candidate b) {
                        return Double.compare(b.weight, a.weight);
                    }
                });
        addFrontier(0, edges, adjacency, visited, queue);
        int reached = 1;
        while (!queue.isEmpty()) {
            Candidate candidate = queue.poll();
            Edge edge = edges.get(candidate.edgeIndex);
            int known = visited[edge.from] ? edge.from : visited[edge.to] ? edge.to : -1;
            int unknown = known == edge.from ? edge.to : known == edge.to ? edge.from : -1;
            if (known < 0 || visited[unknown]) continue;
            Relative relative = known == edge.from
                    ? new Relative(edge.rotation, edge.translation)
                    : invert(edge.rotation, edge.translation);
            Pose base = poses[known];
            double[][] rotation = multiply(relative.rotation, base.rotation);
            double[] translation = add(multiply(relative.rotation, base.translation),
                    relative.translation);
            poses[unknown] = new Pose(rotation, translation);
            visited[unknown] = true;
            treeEdge[candidate.edgeIndex] = true;
            reached++;
            addFrontier(unknown, edges, adjacency, visited, queue);
        }

        List<Double> rotationResiduals = new ArrayList<Double>();
        List<Double> translationResiduals = new ArrayList<Double>();
        int trustedCycleEdges = 0;
        int rejectedEdges = 0;
        for (int i=0;i<edges.size();i++) {
            Edge edge = edges.get(i);
            if (!visited[edge.from] || !visited[edge.to]) continue;
            double[][] predictedR = multiply(poses[edge.to].rotation,
                    transpose(poses[edge.from].rotation));
            double[] predictedT = subtract(poses[edge.to].translation,
                    multiply(predictedR, poses[edge.from].translation));
            double rot = rotationAngleDegrees(multiply(edge.rotation,
                    transpose(predictedR)));
            double trans = directionAngleDegrees(edge.translation, predictedT);
            if (!treeEdge[i] && edge.weight >= 0.35) {
                trustedCycleEdges++;
                rotationResiduals.add(rot);
                translationResiduals.add(trans);
                if (rot > 12.0 || trans > 55.0) rejectedEdges++;
            }
        }
        double medianRotation = median(rotationResiduals);
        double p90Rotation = percentile(rotationResiduals, 0.90);
        double medianTranslation = median(translationResiduals);
        double p90Translation = percentile(translationResiduals, 0.90);
        boolean connected = reached == nodeCount;
        String status;
        if (connected && trustedCycleEdges >= Math.max(2, nodeCount/4)
                && medianRotation <= 2.5 && p90Rotation <= 6.0
                && medianTranslation <= 16.0 && p90Translation <= 35.0
                && rejectedEdges <= Math.max(1, trustedCycleEdges/8)) {
            status = "STRONG";
        } else if (connected && trustedCycleEdges >= 1
                && medianRotation <= 6.0 && p90Rotation <= 14.0
                && medianTranslation <= 30.0 && p90Translation <= 65.0) {
            status = "USABLE";
        } else {
            status = connected ? "INCONSISTENT" : "DISCONNECTED";
        }
        return new Result(true, status, Arrays.asList(poses), reached, nodeCount,
                edges.size(), trustedCycleEdges, rejectedEdges,
                medianRotation, p90Rotation, medianTranslation, p90Translation);
    }

    private static void addFrontier(int node, List<Edge> edges,
                                    List<List<Integer>> adjacency, boolean[] visited,
                                    PriorityQueue<Candidate> queue) {
        for (int index : adjacency.get(node)) {
            Edge edge = edges.get(index);
            int other = edge.from == node ? edge.to : edge.from;
            if (!visited[other]) queue.add(new Candidate(index, edge.weight));
        }
    }

    private static Relative invert(double[][] rotation, double[] translation) {
        double[][] inverseR = transpose(rotation);
        double[] inverseT = multiply(inverseR,
                new double[]{-translation[0], -translation[1], -translation[2]});
        return new Relative(inverseR, inverseT);
    }

    private static double directionAngleDegrees(double[] first, double[] second) {
        double a = norm(first);
        double b = norm(second);
        if (a < 1e-10 || b < 1e-10) return 180.0;
        double dot = (first[0]*second[0]+first[1]*second[1]+first[2]*second[2])/(a*b);
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(Math.acos(dot));
    }

    private static double rotationAngleDegrees(double[][] rotation) {
        double value = (rotation[0][0]+rotation[1][1]+rotation[2][2]-1.0)*0.5;
        value = Math.max(-1.0, Math.min(1.0, value));
        return Math.toDegrees(Math.acos(value));
    }

    private static double median(List<Double> values) {
        return percentile(values, 0.5);
    }

    private static double percentile(List<Double> source, double quantile) {
        if (source.isEmpty()) return 0.0;
        List<Double> values = new ArrayList<Double>(source);
        Collections.sort(values);
        double position = Math.max(0.0, Math.min(1.0, quantile))*(values.size()-1);
        int low = (int)Math.floor(position);
        int high = (int)Math.ceil(position);
        if (low == high) return values.get(low);
        double fraction = position-low;
        return values.get(low)*(1.0-fraction)+values.get(high)*fraction;
    }

    private static double[][] identity() {
        return new double[][]{{1,0,0},{0,1,0},{0,0,1}};
    }
    private static double[][] transpose(double[][] a) {
        double[][] r = new double[3][3];
        for(int i=0;i<3;i++) for(int j=0;j<3;j++) r[i][j]=a[j][i];
        return r;
    }
    private static double[][] multiply(double[][] a,double[][] b) {
        double[][] r=new double[3][3];
        for(int i=0;i<3;i++) for(int k=0;k<3;k++) for(int j=0;j<3;j++) r[i][j]+=a[i][k]*b[k][j];
        return r;
    }
    private static double[] multiply(double[][] a,double[] v) {
        return new double[]{
                a[0][0]*v[0]+a[0][1]*v[1]+a[0][2]*v[2],
                a[1][0]*v[0]+a[1][1]*v[1]+a[1][2]*v[2],
                a[2][0]*v[0]+a[2][1]*v[1]+a[2][2]*v[2]};
    }
    private static double[] add(double[] a,double[] b) {
        return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};
    }
    private static double[] subtract(double[] a,double[] b) {
        return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};
    }
    private static double norm(double[] v) {
        return Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);
    }

    public static final class Edge {
        public final int from;
        public final int to;
        public final double[][] rotation;
        public final double[] translation;
        public final double weight;
        public Edge(int from,int to,double[][] rotation,double[] translation,double weight) {
            this.from=from; this.to=to;
            this.rotation=copy(rotation);
            this.translation=translation==null?null:translation.clone();
            this.weight=weight;
        }
        boolean valid(int nodeCount) {
            return from>=0 && to>=0 && from<nodeCount && to<nodeCount && from!=to
                    && rotation!=null && rotation.length==3 && rotation[0].length==3
                    && translation!=null && translation.length==3
                    && finite(rotation) && finite(translation)
                    && Double.isFinite(weight) && weight>0.0;
        }
    }

    public static final class Pose {
        public final double[][] rotation;
        public final double[] translation;
        Pose(double[][] rotation,double[] translation) {
            this.rotation=copy(rotation);
            this.translation=translation.clone();
        }
        public double[] cameraCenter() {
            double[][] rt=transpose(rotation);
            return multiply(rt,new double[]{-translation[0],-translation[1],-translation[2]});
        }
    }

    public static final class Result {
        public final boolean solved;
        public final String status;
        public final List<Pose> poses;
        public final int reachedNodes;
        public final int totalNodes;
        public final int edgeCount;
        public final int trustedCycleEdges;
        public final int rejectedCycleEdges;
        public final double medianRotationResidualDegrees;
        public final double p90RotationResidualDegrees;
        public final double medianTranslationResidualDegrees;
        public final double p90TranslationResidualDegrees;
        Result(boolean solved,String status,List<Pose> poses,int reachedNodes,int totalNodes,
               int edgeCount,int trustedCycleEdges,int rejectedCycleEdges,
               double medianRotationResidualDegrees,double p90RotationResidualDegrees,
               double medianTranslationResidualDegrees,double p90TranslationResidualDegrees) {
            this.solved=solved; this.status=status;
            this.poses=Collections.unmodifiableList(new ArrayList<Pose>(poses));
            this.reachedNodes=reachedNodes; this.totalNodes=totalNodes; this.edgeCount=edgeCount;
            this.trustedCycleEdges=trustedCycleEdges; this.rejectedCycleEdges=rejectedCycleEdges;
            this.medianRotationResidualDegrees=medianRotationResidualDegrees;
            this.p90RotationResidualDegrees=p90RotationResidualDegrees;
            this.medianTranslationResidualDegrees=medianTranslationResidualDegrees;
            this.p90TranslationResidualDegrees=p90TranslationResidualDegrees;
        }
        static Result failed(String status) {
            return new Result(false,status,Collections.<Pose>emptyList(),0,0,0,0,0,
                    Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY);
        }
        public boolean ready() { return "STRONG".equals(status)||"USABLE".equals(status); }
        public String summary() {
            return "Poses "+reachedNodes+"/"+totalNodes+" · ciclos "+trustedCycleEdges
                    +" · R50 "+format(medianRotationResidualDegrees)+"° · T50 "
                    +format(medianTranslationResidualDegrees)+"° · "+status;
        }
        private static String format(double v) {
            return String.format(java.util.Locale.ROOT,"%.2f",v);
        }
    }

    private static final class Relative {
        final double[][] rotation; final double[] translation;
        Relative(double[][] r,double[] t){rotation=copy(r);translation=t.clone();}
    }
    private static final class Candidate {
        final int edgeIndex; final double weight;
        Candidate(int edgeIndex,double weight){this.edgeIndex=edgeIndex;this.weight=weight;}
    }
    private static double[][] copy(double[][] source) {
        if (source==null) return null;
        double[][] result=new double[source.length][];
        for(int i=0;i<source.length;i++) result[i]=source[i].clone();
        return result;
    }
    private static boolean finite(double[][] values) {
        for(double[] row:values) for(double v:row) if(!Double.isFinite(v)) return false;
        return true;
    }
    private static boolean finite(double[] values) {
        for(double v:values) if(!Double.isFinite(v)) return false;
        return true;
    }
}
