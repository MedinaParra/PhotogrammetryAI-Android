import cl.skm.pulleyai.OrbitBaselinePriorCore;

public final class OrbitBaselinePriorV40Test {
    public static void main(String[] args) {
        OrbitBaselinePriorCore.Result adjacent=OrbitBaselinePriorCore.resolve(
                170.0,-160.0,2.0,3.0,"LOW","LOW");
        if(!adjacent.ready() || Math.abs(adjacent.yawDeltaDegrees-30.0)>1e-9)
            throw new AssertionError(adjacent.summary());
        double expected=2.0*Math.sin(Math.toRadians(15.0));
        if(Math.abs(adjacent.baselineUnits-expected)>1e-9)
            throw new AssertionError(adjacent.summary());
        OrbitBaselinePriorCore.Result vertical=OrbitBaselinePriorCore.resolve(
                20.0,21.0,-2.0,12.0,"LOW","HIGH");
        if(!vertical.ready() || !vertical.crossBand || vertical.baselineUnits<0.38)
            throw new AssertionError(vertical.summary());
        double[] scaled=OrbitBaselinePriorCore.scaleDirection(new double[]{2,0,0},adjacent);
        if(Math.abs(scaled[0]-expected)>1e-9)throw new AssertionError("scale direction");
        System.out.println("OrbitBaselinePriorV40Test OK: "+adjacent.summary()+" | "+vertical.summary());
    }
}
