import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;

/**
 * 计算类
 * Created by ZingBug on 2017/11/1.
 */
public class Calculate {
    private LinkedList<Float> LeyeX;//左眼x轴坐标集合,链表易于频繁数据操作
    private LinkedList<Float> LeyeY;//左眼y轴坐标集合,链表易于频繁数据操作

    private Hashtable<Integer,Float> LeyeSecondX;//用哈希表来存储左眼每秒x轴的平均SPV
    private Hashtable<Integer,Float> LeyeSecondY;//用哈希表来存储左眼每秒y轴的平均SPV

    /*暂时变量*/
    private HashSet<Float> waveSlope_L;//用来保存一个完整锯齿波内的斜率，即一个正斜率，一个负斜率

    private boolean lineBegin_L=false;

    public Calculate()
    {
        this.LeyeX=new LinkedList<>();
        this.LeyeY=new LinkedList<>();
        this.LeyeSecondX=new Hashtable<>();
        this.LeyeSecondY=new Hashtable<>();

        this.waveSlope_L=new HashSet<>();
    }

    public void addLeyeX(float x)
    {
        this.LeyeX.add(x);
    }
    public void addLeyeY(float y)
    {
        this.LeyeY.add(y);
    }
    public synchronized void processLeyeX(int second)
    {
        lineBegin_L=true;
        waveSlope_L.clear();
        float lineDir_L=0f;//方向长度
        int num=0;//用来表示第几个点
        int fastPhaseNum=0;//用于计算快相方向，快相为正则+1，快相为负则-1
        float startX_L;
        float endX_L;
        Hashtable<Integer,Float> points=new Hashtable<>();//两个极值之间的点，用于拟合用
        for(Float x:LeyeX)
        {
            //遍历所有1s，一秒一秒的处理
            //先来判断是否完成一个完整的锯齿波
            if(waveSlope_L.size()==2)
            {

            }
            ++lineDir_L;
            if(lineBegin_L)
            {
                ++num;
                if(num==1)
                {
                    //第一个点
                    startX_L=x;
                    endX_L=x;
                }
                else if(num==2)
                {
                    //第二个点
                }
            }
        }
    }

    //测试用
    public static void main(String[] args)
    {
        WeightedObservedPoints obs=new WeightedObservedPoints();
        obs.add((new BigDecimal(1)).doubleValue(),(new BigDecimal(3)).doubleValue());
        obs.add((new BigDecimal(2)).doubleValue(),(new BigDecimal(5)).doubleValue());
        obs.add((new BigDecimal(3)).doubleValue(),(new BigDecimal(7)).doubleValue());
        /*
        obs.add((new BigDecimal(1)).doubleValue(),3);
        obs.add(2,5);
        obs.add(3,7);ao
        */
        //实现一次多项式拟合
        PolynomialCurveFitter fitter=PolynomialCurveFitter.create(1);
        double[] coeff=fitter.fit(obs.toList());

        BigDecimal bd = new BigDecimal(coeff[1]);
        BigDecimal b=bd.setScale(1,BigDecimal.ROUND_HALF_UP);
        double c=b.floatValue();
        int i=0;

    }
}
