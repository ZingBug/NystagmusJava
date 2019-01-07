import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.bytedeco.javacpp.opencv_videostab;

import java.math.BigDecimal;
import java.util.*;

/**
 * 计算类
 * Created by ZingBug on 2017/11/1.
 */
public class Calculate {

    private final int SLOPE_POINT_NUM=8;
    private final int NOISE_POINT_NUM=3;

    private LinkedList<Double> eyeX;//左眼x轴坐标集合,链表易于频繁数据操作

    private Hashtable<Integer, Double> eyeSecondX;//用哈希表来存储左眼每秒的平均SPV


    /**
     * 构造函数
     */
    public Calculate() {
        this.eyeX = new LinkedList<Double>();//初始化左眼X轴坐标容器

        this.eyeSecondX = new Hashtable<Integer, Double>();//哈希表初始化

    }

    /**
     * 添加X轴坐标
     *
     * @param x x轴坐标添加值
     */
    public void addEyeX(double x) {
        this.eyeX.add(x);
    }


    /**
     * 处理眼睛X轴坐标并保存秒的平均SPV,上锁
     *
     * @param second 当前秒数
     */
    public void processEyeX(int second) throws NumberFormatException {
        List<Double> slowSlope = new LinkedList<>();//慢相斜率集合

        HashMap<Integer, Double> points = new HashMap<>();//两个极值点之间的点，用于拟合

        boolean findMin = false;

        int max = 0;
        int min = 0;

        int len = eyeX.size();
        for (int i = 0; i < len; i++) {
            double x = eyeX.get(i);

            if (!findMin) {
                //找局部最大极值点
                max = findMax(eyeX, i);
                i = max;
                findMin = true;
                points.clear();
            } else {
                //找局部最小极值点
                min = findMin(eyeX, i, points);
                i = min;

                if (points.size() > SLOPE_POINT_NUM) {
                    System.out.println(points.size());
                    double slope = getSlope(points);
                    slowSlope.add(slope);
                }

                points.clear();//点清零，开始寻找局部最大极值点
                findMin = false;
            }
        }
        eyeX.clear();//清除剩下的所有数据了
        double sumSPV = 0f;

        if (slowSlope.size() > 0) {
            //队列不为空时
            for (double tempSPV : slowSlope) {
                sumSPV += tempSPV;
            }
            eyeSecondX.put(second, sumSPV / slowSlope.size());//存入每秒的平均SPV
        } else {
            //队列为空时
            eyeSecondX.put(second, sumSPV);//存入每秒的平均SPV
        }

    }

    private int judgeNoise(LinkedList<Double> list, double last, int index, boolean dir) {
        int len = list.size();
        for (int i = index; i < len && i - index < NOISE_POINT_NUM; i++) {
            if ((dir && list.get(i) < last) || (!dir && list.get(i) > last)) {
                return i;
            }
        }
        return -1;
    }

    private int findMax(LinkedList<Double> list, int index) {
        int len = list.size();
        double pre = list.get(index);
        double cur;
        for (int i = index + 1; i + 1 < len; i++) {
            cur = list.get(i);
            if (cur > pre && cur > list.get(i + 1)) {
                //去除噪声
                int n = judgeNoise(list, cur, i, false);
                if (n == -1) {
                    return i;
                }
                i = n + 1;
            }
            pre = cur;
        }
        return index;
    }

    private int findMin(LinkedList<Double> list, int index, HashMap<Integer, Double> points) {
        points.clear();
        int len = list.size();
        double pre = list.get(index);
        double cur;
        for (int i = index + 1; i + 1 < len; i++) {
            cur = list.get(i);

            if (cur < pre && cur < list.get(i + 1)) {
                //去除噪声
                int n = judgeNoise(list, cur, i, true);
                if (n == -1) {
                    return i;
                }
                i = n + 1;
            }
            points.put(i, cur);
            pre = cur;
        }
        return index;
    }

    /**
     * 根据最小二乘法拟合得到直线斜率
     *
     * @param points 点的集合
     * @return 拟合斜率
     */
    private double getSlope(HashMap<Integer, Double> points) {
        if (points.size() <= 1) {
            return 0;
        }
        WeightedObservedPoints obs = new WeightedObservedPoints();
        Iterator iter = points.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry element = (Map.Entry) iter.next();
            int key = (int) element.getKey();
            double value = (double) element.getValue();
            obs.add(key, value);
        }
        PolynomialCurveFitter fitter = PolynomialCurveFitter.create(1);
        double[] coeff = fitter.fit(obs.toList());

        BigDecimal bd = new BigDecimal(coeff[1]);
        BigDecimal slope = bd.setScale(3, BigDecimal.ROUND_HALF_UP);
        return Math.abs(slope.doubleValue()) * 50;
    }


    public double getSPV(int second) {
        if (eyeSecondX.containsKey(second)) {
            return eyeSecondX.get(second);
        }
        return 0;
    }


    /**
     * 取一个完整波形斜率中的绝对值的小值,返回也是绝对值
     *
     * @param slopes 哈希队列
     * @return 哈希队列中所有元素绝对值的最小值，但返回值为绝对值，非正常值
     */
    private double getMiniSlope(LinkedList<Double> slopes) {
        double slope = Double.POSITIVE_INFINITY;//初始值为最大值
        for (double s : slopes) {
            double temp = Math.abs(s);
            if (temp < slope) {
                slope = temp;
            }
        }
        return slope;
    }

    /**
     * 取一个完整波形斜率中的绝对值的小值,返回是原始值,非绝对值
     *
     * @param slopes 哈希队列
     * @return 哈希队列中所有元素绝对值的最小值，但返回值为正常值，非绝对值
     */
    private double getMaxSlope(LinkedList<Double> slopes) {
        double absSlope = Double.NEGATIVE_INFINITY;
        double slope = 0f;
        for (double s : slopes) {
            double temp = Math.abs(s);
            if (temp > absSlope) {
                absSlope = temp;
                slope = s;
            }
        }
        return slope;
    }
}

