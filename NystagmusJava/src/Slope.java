import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ZingBug on 2019/1/14.
 */
public class Slope implements Comparable {

    private final int SLOPE_POINT_MIN_1=25;
    private final int SLOPE_POINT_MAX = 300;

    private final int AMPLITUDE_MIN_1 = 9;
    private final int AMPLITUDE_MAX = 50;

    private final int AMPLITUDE_MIN_2 = 0;
    private final int SLOPE_POINT_MIN_2=5;

    private int startIndex;
    private int endIndex;
    private double startValue;
    private double endValue;

    private boolean exist = false;


    public Slope(int startIndex, double startValue, int endIndex, double endValue,boolean normal) {
        this.startIndex = startIndex;
        this.startValue = startValue;
        this.endIndex = endIndex;
        this.endValue = endValue;

        int minAmplitude=normal?AMPLITUDE_MIN_1:AMPLITUDE_MIN_2;
        int minSlopePoint=normal?SLOPE_POINT_MIN_1:SLOPE_POINT_MIN_2;

        if (getSize() < SLOPE_POINT_MAX && getSize() > minSlopePoint && getAmplitude() > minAmplitude && getAmplitude() < AMPLITUDE_MAX) {
            this.exist = true;
        }
    }

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public double getStartValue() {
        return startValue;
    }

    public void setStartValue(double startValue) {
        this.startValue = startValue;
    }

    public double getEndValue() {
        return endValue;
    }

    public void setEndValue(double endValue) {
        this.endValue = endValue;
    }

    public int getSize() {
        return endIndex - startIndex;
    }

    public double getAmplitude() {
        return Math.abs(endValue - startValue);
    }

    public double getSlope() {
        return Math.abs((endValue - startValue) / (endIndex - startIndex)) * 50;
    }

    public boolean isExist() {
        return exist;
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof Slope) {
            Slope s = (Slope) o;
            return Double.compare(s.getSize(),this.getSize());
        } else {
            throw new ClassCastException("类型不匹配");
        }
    }

    @Override
    public String toString() {
        return "帧位置：" + getStartIndex() + " - " + getEndIndex() + "\t" +
                "帧数目:" + getSize() + "\t" +
                "赋值：" + getAmplitude() + "\t" +
                "斜率大小:" + getSlope();
    }
}
