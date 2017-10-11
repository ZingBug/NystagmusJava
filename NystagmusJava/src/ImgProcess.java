import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacpp.opencv_imgproc;

import java.util.Vector;

/**
 * Created by ZingBug on 2017/10/11.
 */
public class ImgProcess {
    private Size size=new Size(9,9);

    private Mat Leye;
    private double EyeRatio;
    private Mat OriginalLeye;
    private CvRect Lrect=new CvRect();
    private double Lmaxarea=0;//左眼最大轮廓
    private int LmaxAreaIndex=0;//左眼最大轮廓下标
    private Vector<Box> Lcircles=new Vector<Box>();//用于保存轮廓点
    private IplImage LeyeImage;//左眼保存为IplImage格式

    private CvScalar cvwhite=new CvScalar(255,255,255,0);
    private CvScalar cvblue=new CvScalar(0,0,255,0);
    private CvScalar cvgreen=new CvScalar(0,255,0,0);
    private CvScalar cvred=new CvScalar(255,0,0,0);

    public ImgProcess()
    {}

    /**
     * 初始化
     * @param leye 左眼图像
     * @param eyeratio 眼睛闭眼长宽比例
     */
    public void Start(Mat leye,double eyeratio)
    {
        Leye=new Mat(leye);
        EyeRatio=eyeratio;
        LeyeImage=new IplImage(leye);
        //保存原始图像
        OriginalLeye=new Mat(leye);
    }

    /**
     * 输出左眼图像
     * @return 左眼图像
     */
    public Mat OutLeye()
    {
        return Leye;
    }
    /**
     * 滤波、灰度化等处理，返回图像便于边缘检测
     * @param grayimg0 源图像
     * @return 灰度图像
     */
    private Mat GrayDetect(Mat grayimg0)
    {
        Mat grayimg=new Mat(grayimg0);

        opencv_imgproc.cvtColor(grayimg,grayimg,opencv_imgproc.COLOR_RGB2GRAY);//灰度化处理
        //opencv_imgproc.medianBlur(grayimg,grayimg,9);//中值滤波
        //opencv_imgproc.blur(grayimg,grayimg,size);//均值滤波
        Mat grayout=Binary(grayimg,50);
        return grayout;
    }
    /**
     * 二值化处理
     * @param binaryimg 源图像
     * @param value 二值化阈值
     * @return 二值化图像
     */
    private Mat Binary(Mat binaryimg,int value)
    {
        Mat binaryout=new Mat();
        opencv_imgproc.threshold(binaryimg,binaryout,value,255,opencv_imgproc.THRESH_BINARY);
        return binaryout;
    }
    /**
     * 二乘法拟合圆
     * @param points 待拟合点的集合
     * @return 拟合圆，包括圆心坐标和半径
     */
    private Box circleLeastFit(Vector<Point> points)
    {
        Box box=new Box(0.0d,0.0d,0.0d);
        long Sum=points.size();
        //如果少于三点，不能拟合圆，直接返回
        if(Sum<3)
        {
            return box;
        }
        int i;
        double X1 = 0;
        double Y1 = 0;
        double X2 = 0;
        double Y2 = 0;
        double X3 = 0;
        double Y3 = 0;
        double X1Y1 = 0;
        double X1Y2 = 0;
        double X2Y1 = 0;

        for (i = 0; i < Sum; ++i)
        {
            X1 += points.get(i).x();
            Y1 += points.get(i).y();
            X2 += points.get(i).x()*points.get(i).x();
            Y2 += points.get(i).y()*points.get(i).y();
            X3 += points.get(i).x()*points.get(i).x()*points.get(i).x();
            Y3 += points.get(i).y()*points.get(i).y()*points.get(i).y();
            X1Y1 += points.get(i).x()*points.get(i).y();
            X1Y2 += points.get(i).x()*points.get(i).y()*points.get(i).y();
            X2Y1 += points.get(i).x()*points.get(i).x()*points.get(i).y();
        }

        double C, D, E, G, H, N;
        double a, b, c;
        N = points.size();
        C = N*X2 - X1*X1;
        D = N*X1Y1 - X1*Y1;
        E = N*X3 + N*X1Y2 - (X2 + Y2)*X1;
        G = N*Y2 - Y1*Y1;
        H = N*X2Y1 + N*Y3 - (X2 + Y2)*Y1;
        a = (H*D - E*G) / (C*G - D*D);
        b = (H*C - E*D) / (D*D - G*C);
        c = -(a*X1 + b*Y1 + X2 + Y2) / N;

        box.setX(a/(-2));
        box.setY(b/(-2));
        box.setR(Math.sqrt(a*a + b*b - 4 * c) / 2);
        return box;
    }
    /**
     * 边缘检测
     * @param edgeimg 检测图像
     * @return 边缘图像
     */
    private Mat EdgeDetect(Mat edgeimg)
    {
        Mat edgeout=new Mat();
        opencv_imgproc.Canny(edgeimg,edgeout,100,250,3,false);
        return edgeout;
    }
    /**
     * 绘制源
     * @param circles 需要绘制的圆的集合
     * @param midImage 源图像
     */
    private void PlotC(Vector<Box> circles,IplImage midImage)
    {
        for(int i=0;i<circles.size();++i)
        {
            CvPoint center=new CvPoint((int)Math.round(circles.get(i).getX()),(int)Math.round(circles.get(i).getY()));
            int radius=(int)circles.get(i).getR();
            //opencv_imgproc.cvCircle(midImage,center,1,cvblue,-1,8,0);//画圆心
            opencv_imgproc.cvCircle(midImage,center,radius,cvred,1,8,0);//画圆轮廓
            drawCross(midImage,center,cvwhite,1);//绘制十字光标
        }
    }
    /**
     * 绘制十字光标
     * @param img 源图像
     * @param point 十字点
     * @param color 颜色
     * @param thickness 线宽
     */
    private void drawCross(IplImage img,CvPoint point,CvScalar color,int thickness)
    {
        int heigth=img.height();
        int width=img.width();
        CvPoint above=new CvPoint(point.x(),0);
        CvPoint below=new CvPoint(point.x(),heigth);
        CvPoint left=new CvPoint(0,point.y());
        CvPoint right=new CvPoint(width,point.y());
        //绘制横线
        opencv_imgproc.cvLine(img,left,right,color,thickness,8,0);
        //绘制竖线
        opencv_imgproc.cvLine(img,above,below,color,thickness,8,0);
        return;
    }

    /**
     * 图像识别
     */
    public void ProcessSeparate()
    {
        Mat Lgrayimg;
        Mat Ledgeimg;
        double temparea;
        IplImage LmatImage;
        CvMemStorage Lstorage=CvMemStorage.create();
        CvSeq cvLcontour=new CvSeq(null);
        CvSeq cvtempLcontour=new CvSeq(null);

        Lgrayimg=GrayDetect(Leye);
        Ledgeimg=EdgeDetect(Lgrayimg);
        LmatImage=new IplImage(Ledgeimg);
        opencv_imgproc.cvFindContours(LmatImage,Lstorage,cvLcontour, Loader.sizeof(CvContour.class), opencv_imgproc.CV_RETR_CCOMP, opencv_imgproc.CV_CHAIN_APPROX_NONE);

        if(!cvLcontour.isNull()&&cvLcontour.elem_size()>0)
        {
            //左眼有轮廓
            while (cvLcontour != null && !cvLcontour.isNull())
            {
                temparea=opencv_imgproc.cvContourArea(cvLcontour);
                if(temparea>Lmaxarea)
                {
                    Lmaxarea=temparea;
                    cvtempLcontour=cvLcontour;
                    continue;
                }
                cvLcontour=cvLcontour.h_next();
            }
            if(!cvtempLcontour.isNull())
            {
                Vector<Point> leftPoints=new Vector<Point>();
                for(int i=0;i<cvtempLcontour.total();++i)
                {
                    Point p=new Point(opencv_core.cvGetSeqElem(cvtempLcontour,i));
                    leftPoints.add(p);
                }
                //闭眼检测
                Lrect=opencv_imgproc.cvBoundingRect(cvtempLcontour);
                if((Lrect.width()/(float)Lrect.height())<EyeRatio&&(Lrect.height()/(float)Lrect.width())<EyeRatio&&Lrect.width()>0&&Lrect.height()>0)
                {
                    Box Lbox=circleLeastFit(leftPoints);//左眼拟合圆检测
                    if(Lbox.getR()!=0)
                    {
                        //如果半径不为0
                        Lcircles.add(Lbox);
                    }
                }
            }
        }
        if(Lcircles.size()>0)
        {
            PlotC(Lcircles,LeyeImage);
        }
        Leye=new Mat(LeyeImage);
    }
}
