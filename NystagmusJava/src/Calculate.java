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
    private LinkedList<Double> LeyeX;//左眼x轴坐标集合,链表易于频繁数据操作
    private LinkedList<Double> LeyeY;//左眼y轴坐标集合,链表易于频繁数据操作

    private Hashtable<Integer,Double> LeyeSecondSPVX;//用哈希表来存储左眼每秒x轴的平均SPV
    private Hashtable<Integer,Double> LeyeSecondSPVY;//用哈希表来存储左眼每秒y轴的平均SPV
    private Hashtable<Integer,Integer> LeyeSecondFastPhaseNumX;//用哈希表来存储左眼每秒轴的快相个数
    private Hashtable<Integer,Integer> LeyeSecondFastPhaseNumY;//用哈希表来存储左眼每秒y轴的快相个数

    /*暂时变量*/
    private LinkedList<Double> waveSlope_L;//用来保存一个完整锯齿波内的斜率，即一个正斜率，一个负斜率

    private boolean lineBegin_L=false;

    public Calculate()
    {
        this.LeyeX=new LinkedList<>();
        this.LeyeY=new LinkedList<>();
        this.LeyeSecondSPVX=new Hashtable<>();
        this.LeyeSecondSPVY=new Hashtable<>();
        this.LeyeSecondFastPhaseNumX=new Hashtable<>();
        this.LeyeSecondFastPhaseNumY=new Hashtable<>();

        this.waveSlope_L=new LinkedList<>();
    }

    public void addLeyeX(double x)
    {
        this.LeyeX.add(x);
    }
    public void addLeyeY(double y)
    {
        this.LeyeY.add(y);
    }
    public synchronized void processLeyeX(int second)
    {
        lineBegin_L=true;
        waveSlope_L.clear();
        List<Double> slowSlope=new LinkedList<>();//慢相集合
        boolean lineDir_L=true;//斜率方向
        int fastPhaseNum=0;//用于计算快相方向，快相为正则+1，快相为负则-1
        double endX_L=0f;
        Hashtable<Integer,Double> points=new Hashtable<>();//两个极值之间的点，用于拟合用
        int frameNum=0;
        for(Double x:LeyeX)
        {
            //遍历所有1s，一秒一秒的处理
            frameNum++;
            //先来判断是否完成一个完整的锯齿波
            if(waveSlope_L.size()==2)
            {
                /*取慢相，返回值为绝对值*/
                slowSlope.add(getMiniSlope(waveSlope_L));
                /*取快相，返回值为正常值，快相只用来判断方向*/
                double fastSlope=getMaxSlope(waveSlope_L);
                if(fastSlope>0)
                {
                    ++fastPhaseNum;
                }
                else if(fastSlope<0)
                {
                    --fastPhaseNum;
                }
                waveSlope_L.clear();
            }
            if(lineBegin_L)
            {
                //开始时间
                if(frameNum==1)
                {
                    //第一个点
                    endX_L=x;
                }
                else if(frameNum==2)
                {
                    //第二个点
                    if(x>=endX_L)
                    {
                        //正斜率
                        lineDir_L=true;
                    }
                    else
                    {
                        //负斜率
                        lineDir_L=false;
                    }
                    endX_L=x;
                    lineBegin_L=false;//开始已经结束
                }
            }
            else
            {
                //正常时间
                if(x>endX_L)
                {
                    //正斜率，往上走
                    if(!lineDir_L)
                    {
                        //如果之前是往下走的，现在已经往上走了，所以代表之前那段直线结束
                        //开始计算上一段负斜率
                        if(points.size()>4)
                        {
                            //上一段直线大于4个点的时候才进行检测
                            double slope=getSlope(points);
                            waveSlope_L.add(slope);
                        }
                        //保存极值小点
                        double min=points.get(frameNum-1);
                        points.clear();
                        //将上一次的极值点放入下一次计算直线中
                        points.put(frameNum-1,min);
                    }
                    lineDir_L=true;
                    endX_L=x;
                }
                else
                {
                    //负斜率，往下走
                    if(lineDir_L)
                    {
                        //如果之前是往上走的，现在已经往下走了，所以代表之前那段直线结束
                        //开始计算上一段正斜率
                        if(points.size()>4)
                        {
                            //上一段直线大于4个点的时候才进行检测
                            double slope=getSlope(points);
                            waveSlope_L.add(slope);
                        }
                        //保存极值大点
                        double max=points.get(frameNum-1);
                        points.clear();
                        //将上一次极值点放入下一次计算直线中
                        points.put(frameNum-1,max);
                    }
                    lineDir_L=false;
                    endX_L=x;
                }
            }
            points.put(frameNum,x);
        }
        LeyeX.clear();//清空数据
        double sumSPV=0f;

        if(slowSlope.size()>0)
        {
            //存在慢相
            for(double tempSPV:slowSlope)
            {
                sumSPV+=tempSPV;
            }
            LeyeSecondSPVX.put(second,sumSPV/slowSlope.size());//存入每秒的平均SPV
        }
        else
        {
            //不存在慢相
            LeyeSecondSPVX.put(second,sumSPV);
        }
        LeyeSecondFastPhaseNumX.put(second,fastPhaseNum);//存入快相个数
    }
    //用于判断眼睛快相方向 返回ture:左  false:右
    public boolean judgeFastPhase()
    {
        int num=0;
        for(int n:LeyeSecondFastPhaseNumX.values())
        {
            num+=n;
        }
        return num<0;
    }
    //返回某秒的平均SPV
    public double getSecondSPV(int second)
    {
        if(LeyeSecondSPVX.containsKey(second))
        {
            return LeyeSecondSPVX.get(second);
        }
        else
        {
            throw new NoSuchElementException("no element");
        }
    }
    //根据最小二乘法拟合得到直线斜率
    private double getSlope(Hashtable<Integer,Double> points)
    {
        WeightedObservedPoints obs=new WeightedObservedPoints();
        Enumeration e=points.keys();
        int key;
        while (e.hasMoreElements())
        {
            key=(int)e.nextElement();
            obs.add(key,points.get(key));
        }
        PolynomialCurveFitter fitter=PolynomialCurveFitter.create(1);
        double[] coeff=fitter.fit(obs.toList());

        BigDecimal bd = new BigDecimal(coeff[1]);
        BigDecimal slope=bd.setScale(3,BigDecimal.ROUND_HALF_UP);
        return slope.doubleValue()*10;
    }
    //取一个完整波形斜率中的绝对值的小值,返回也是绝对值
    private double getMiniSlope(LinkedList<Double> slopes)
    {
        double slope=Double.POSITIVE_INFINITY;//初始化为最大值
        for(double s:slopes)
        {
            double temp=Math.abs(s);
            if(temp<slope)
            {
                slope=temp;
            }
        }
        return slope;
    }
    //取一个完整波形斜率中的绝对值的小值,返回是原始值,非绝对值
    private double getMaxSlope(LinkedList<Double> slopes)
    {
        double absSlope= Double.NEGATIVE_INFINITY;
        double slope=0f;
        for(double s:slopes)
        {
            double temp= Math.abs(s);
            if(temp>absSlope)
            {
                absSlope=temp;
                slope=s;
            }
        }
        return slope;
    }
}
