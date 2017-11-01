import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 * 眼震中心点滤波
 * Created by ZingBug on 2017/11/1.
 */
public class PointFilter {
    private final int N;
    private LinkedList<Box> points;
    private Box value[];
    private int index;
    public PointFilter(int N)
    {
        points=new LinkedList<>();
        value=new Box[N];
        index=0;
        this.N=N;
        for(int i=0;i<N;i++)
        {
            value[i]=new Box(0,0,0);
        }
    }
    public PointFilter()
    {
        this(5);
    }
    public void add(Box point)
    {
        points.addLast(point);
    }
    public Box get() throws NoSuchElementException
    {
        if(!isGet())
        {
            throw new NoSuchElementException("No elements");
        }
        value[index++]=points.removeFirst();
        if(index==N)
        {
            index=0;
        }
        float sumX=0f;
        float sumY=0f;
        float sumR=0f;

        for(Box box:value)
        {
            sumX+=box.getX();
            sumY+=box.getY();
            sumR+=box.getR();
        }

        return new Box(sumX/value.length,sumY/value.length,sumR/value.length);
    }
    public boolean isGet()
    {
        return points.size()>0;
    }
    //测试用
    public static void main(String[] args)
    {
        PointFilter filter=new PointFilter();
        filter.add(new Box(1,0,0));
        filter.add(new Box(2,0,0));
        filter.add(new Box(3,0,0));
        filter.add(new Box(4,0,0));
        filter.add(new Box(5,0,0));
        filter.add(new Box(6,0,0));
        filter.add(new Box(7,0,0));
        filter.add(new Box(8,0,0));
        Box box[]=new Box[8];
        for(int i=0;i<8;i++)
        {
            box[i]=filter.get();
        }
        JFrame frame=new JFrame("Test Chart");
        WaveChart rtcp=new WaveChart("X轴坐标","眼震波形","坐标");
        frame.getContentPane().add(rtcp,new BorderLayout().CENTER);
        frame.pack();
        frame.setVisible(true);
        (new Thread(rtcp)).start();
        frame.addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent windowevent)
            {
                System.exit(0);
            }

        });

        for(int i=0;i<500;i++)
        {
            rtcp.add(i,Math.random()*100);
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException e)  {   }
        }

    }
}
