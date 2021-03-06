package distance;

/**
 * Created by loongkuan on 26/03/16.
 **/
public class KullbackLeibler extends Distance{

    @Override
    public double findDistance(double[] p, double[] q, double[] weights) {
        assert p.length == q.length;
        double driftDistance = 0.0f;
        for (int i = 0; i < p.length; i++) {
            driftDistance += (p[i] * (Math.log(p[i] / q[i]) / Math.log(2))) * weights[i];
        }
        return driftDistance;
    }
}
