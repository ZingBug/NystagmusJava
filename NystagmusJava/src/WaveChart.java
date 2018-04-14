import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.StandardChartTheme;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.function.BiConsumer;

/**
 * Created by ZingBug on 2017/11/1.
 */
public class WaveChart extends ChartBasePanel implements Runnable {
    private XYSeries XYSeries;
    private Font font=new Font("宋体",Font.PLAIN,12);
    private ChartPanel chartPanel;
    private JFreeChart chart;  // 创建一个JFreeChart时间序列图表

    public WaveChart(String chartContent,String title,String yaxisName)
    {
        super();
        this.chart=createChart(chartContent,title,yaxisName);
        addChart(this.chart);

        // 将JFreeChart放在专用的图表容器ChartPanel中
        this.chartPanel = new ChartPanel(this.chart);
        this.chartPanel.setPreferredSize(new Dimension(600, 350));

        // 设置chartPanel容器边框
        CompoundBorder compoundBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(1, 1,1, 1),
                BorderFactory.createEtchedBorder());
        this.chartPanel.setBorder(compoundBorder);

        // 将chartPanel加入到本容器中
        add(this.chartPanel);
    }
    private JFreeChart createChart(String chartContent, String title, String yaxisName){
        //创建时序图对象
        XYSeries = new XYSeries(chartContent);
        XYSeriesCollection xySeriesCollection=new XYSeriesCollection(XYSeries);

        // 设置中文主题样式 解决乱码
        StandardChartTheme chartTheme = new StandardChartTheme("CN");
        // 设置标题字体
        chartTheme.setExtraLargeFont(font);
        // 设置图例的字体
        chartTheme.setRegularFont(font);
        // 设置轴向的字体
        chartTheme.setLargeFont(font);
        chartTheme.setSmallFont(font);

        ChartFactory.setChartTheme(chartTheme);

        JFreeChart jfreechart = ChartFactory.createXYLineChart(title,"Time/s",yaxisName,xySeriesCollection);
        XYPlot xyplot = jfreechart.getXYPlot();
        //纵坐标设定
        ValueAxis valueaxis = xyplot.getDomainAxis();
        //自动设置数据轴数据范围
        valueaxis.setAutoRange(true);
        //数据轴固定数据范围 30s
        //valueaxis.setFixedAutoRange(100);

        valueaxis = xyplot.getRangeAxis();

        return jfreechart;
    }

    @Override
    public void run()
    {
    }
    public void add(double a,double b)
    {
        XYSeries.add(a,b);
    }
    public void clear()
    {
        XYSeries.clear();
    }

}
