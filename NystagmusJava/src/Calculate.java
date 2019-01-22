import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.bytedeco.javacpp.opencv_videostab;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * 计算类
 * Created by ZingBug on 2017/11/1.
 */
public class Calculate {

    private boolean dir = true;//true代表求上升斜率，false代表求下降斜率

    private final int NOISE_POINT_NUM = 1;
    private final int SLOPE_SIZE = 9;
    private final int MEDIAN_SLOPE_COUNT = 5;


    private LinkedList<Double> eyeX;//左眼x轴坐标集合,链表易于频繁数据操作

    private Hashtable<Integer, Double> eyeSecondX;//用哈希表来存储左眼每秒的平均SPV

    private FileWriter fw;

    /**
     * 构造函数
     */
    public Calculate() {
        this.eyeX = new LinkedList<Double>();//初始化左眼X轴坐标容器

        this.eyeSecondX = new Hashtable<Integer, Double>();//哈希表初始化

        try {
            fw = new FileWriter(GlobalValue.saveDataPath + "/spv.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
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

        HashMap<Integer, Double> points = new HashMap<>();//两个极值点之间的点，用于拟合

        boolean findMin = false;

        TreeSet<Slope> set = new TreeSet<>();//慢相集合
        TreeSet<Slope> set_ab = new TreeSet<>();//慢相集合，特殊情况

        int len = eyeX.size();

        for (int i = 0; i < len; i++) {
            double x = eyeX.get(i);

            if (!findMin) {
                //找局部最大极值点
                i = findMax(eyeX, i, points);
                if (dir && i < len) {
                    judgeSlope(points, set, set_ab);
                }
                findMin = true;
                points.clear();
            } else {
                //找局部最小极值点
                i = findMin(eyeX, i, points);
                if ((!dir) && i < len) {
                    judgeSlope(points, set, set_ab);
                }
                findMin = false;
                points.clear();//点清零，开始寻找局部最大极值点
            }
        }
        if (set.size() > 0) {
            //正常队列不为空时
            double slope_normal = getSlope(set);
            double slope_abnormal = getSlope(set_ab);
            double slope_final = slope_normal != 0 ? slope_normal : slope_abnormal;
            eyeSecondX.put(second, slope_final);//存入平均SPV
        } else {
            //所有队列都为空

            eyeSecondX.put(second, 0d);//存入每秒的平均SPV
        }
        eyeX.clear();//清除剩下的所有数据了
    }

    private double getSlope(TreeSet<Slope> set) {
        if (set == null || set.size() < 1) {
            return 0;
        }
        int i = 0;
        double sumSPV = 0d;
        List<Slope> slopes = new ArrayList<>();
        for (Slope slope : set) {
            if (!slope.isExist())//如果斜率不合规
            {
                continue;
            }
            if (i == SLOPE_SIZE) {
                break;
            }
            System.out.println(slope);
            //sumSPV+=slope.getSlope();
            slopes.add(slope);
            i++;
        }
        if (slopes.size() == 0) {
            return 0;
        }
        System.out.println("******************去除最大最小值*****************");
        slopes = getSlopeByRmoveMaxMin(slopes);
        for (Slope slope : slopes) {
            System.out.println(slope);
            sumSPV += slope.getSlope();
        }
        return sumSPV / slopes.size();
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

    private int findMax(LinkedList<Double> list, int index, HashMap<Integer, Double> points) {
        points.clear();
        int len = list.size();
        double pre = list.get(index);
        double cur;
        points.put(index, pre);
        for (int i = index + 1; i + 1 < len; i++) {
            cur = list.get(i);
            if (cur < pre) {
                //去除噪声
                int n = judgeNoise(list, cur, i, false);
                if (n == -1) {
                    outText(i + "  " + cur);
                    points.put(i, cur);
                    return i;
                }
                i = n;
            }
            cur = list.get(i);
            outText(i + "  " + cur);
            points.put(i, cur);
            pre = cur;
        }
        return len;
    }

    private int findMin(LinkedList<Double> list, int index, HashMap<Integer, Double> points) {
        points.clear();
        int len = list.size();
        double pre = list.get(index);
        points.put(index, pre);
        double cur;
        for (int i = index + 1; i + 1 < len; i++) {
            cur = list.get(i);
            if (cur > pre) {
                //去除噪声
                int n = judgeNoise(list, cur, i, true);
                if (n == -1) {
                    outText(i + "  " + cur);
                    points.put(i, cur);
                    return i;
                }
                i = n;
            }
            cur = list.get(i);
            outText(i + "  " + cur);
            points.put(i, cur);
            pre = cur;
        }
        return len;
    }

    private void judgeSlope(HashMap<Integer, Double> points, TreeSet<Slope> set, TreeSet<Slope> set_ab) {
        Iterator iter = points.entrySet().iterator();
        int minIndex = Integer.MAX_VALUE;
        int maxIndex = Integer.MIN_VALUE;
        while (iter.hasNext()) {
            Map.Entry element = (Map.Entry) iter.next();
            int key = (int) element.getKey();
            if (key < minIndex) {
                minIndex = key;
            }
            if (key > maxIndex) {
                maxIndex = key;
            }
        }
        double min = points.get(minIndex);
        double max = points.get(maxIndex);
        Slope slope = new Slope(minIndex, min, maxIndex, max, true);
        Slope slope_ab = new Slope(minIndex, min, maxIndex, max, false);
        set.add(slope);
        set_ab.add(slope_ab);
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

    private double getSlope(HashMap<Integer, Double> points, int minIndex, int maxIndex) {
        double min = points.get(minIndex);
        double max = points.get(maxIndex);
        return Math.abs((max - min) / (maxIndex - minIndex)) * 50;
    }

    private List<Slope> getSlopeByMedianFilter(List<Slope> slopes) {
        if (slopes == null || slopes.size() <= 4) {
            return slopes;
        }

        //int medianSlopeCount = slopes.size()/2+1;//必须保证奇数
        int medianSlopeCount = 3;//必须保证奇数
        List<Slope> newSlopes = new ArrayList<>();

        for (int i = 0; i < slopes.size(); i++) {
            List<Slope> medianSlope = new ArrayList<>();//邻域
            int count = medianSlopeCount;
            int step = 1;
            //先取左边，再取右边
            boolean left = true;
            medianSlope.add(slopes.get(i));
            while (count-- > 1) {
                int index = 0;
                if (left) {
                    index = i - step;
                    if (index < 0) {
                        index = slopes.size() - Math.abs(index);
                    }
                } else {
                    index = i + step;
                    if (index >= slopes.size()) {
                        index = index - slopes.size();
                    }
                    step++;
                }
                left = !left;
                medianSlope.add(slopes.get(index));
            }
            //排序
            medianSlope.sort(new Comparator<Slope>() {
                @Override
                public int compare(Slope o1, Slope o2) {
                    return Double.compare(o1.getSlope(), o2.getSlope());
                }
            });
            //取中值
            newSlopes.add(medianSlope.get(medianSlopeCount / 2));
        }
        return newSlopes;
    }

    private List<Slope> getSlopeByRmoveMaxMin(List<Slope> slopes) {
        if (slopes == null || slopes.size() < 3) {
            return slopes;
        }
        //按照从小到大排序
        slopes.sort(new Comparator<Slope>() {
            @Override
            public int compare(Slope o1, Slope o2) {
                return Double.compare(o1.getSlope(), o2.getSlope());
            }
        });
        slopes.remove(slopes.size() - 1);//去掉最大值
        //slopes.remove(0);//去掉最小值
        return slopes;
    }

    public double getSPV(int second) {
        if (eyeSecondX.containsKey(second)) {
            return eyeSecondX.get(second);
        }
        return 0;
    }

    private void outText(String message) {

        try {
            fw.write(message + System.getProperty("line.separator"));
        } catch (IOException e) {
            System.out.println(e.toString());
        }

    }

    public void closeText() {

        try {
            fw.close();
        } catch (IOException e) {
            System.out.println(e.toString());
        }

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

