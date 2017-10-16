import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Vector;

/**
 * Created by ZingBug on 2017/10/11.
 */
public class ImgProcess {
    private static OpenCVFrameConverter.ToIplImage matConverter = new OpenCVFrameConverter.ToIplImage();//Mat转Frame
    private static Java2DFrameConverter frameConverter=new Java2DFrameConverter();

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
    private CvScalar cvblack=new CvScalar(0,0,0,0);

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
        opencv_imgproc.medianBlur(grayimg,grayimg,9);//中值滤波
        //opencv_imgproc.blur(grayimg,grayimg,size);//均值滤波

        /*重构开运算，去除光斑*/
        Mat element_open=opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE,new Size(15,15));//形态学开运算的内核
        opencv_imgproc.morphologyEx(grayimg,grayimg,opencv_imgproc.MORPH_OPEN,element_open);//开运算

        /*闭运算，去除睫毛*/
        Mat element_close=opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE,new Size(9,9));//形态学闭运算的内核
        opencv_imgproc.morphologyEx(grayimg,grayimg,opencv_imgproc.MORPH_CLOSE,element_close);

        /*顶帽+低帽变换，将源图像加上低帽变换再减去顶帽变换，用以增强对比度*/
        Mat element_hot=opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE,new Size(5,5));
        Mat topHat=new Mat();
        Mat bottomHat=new Mat();
        Mat tempHat=new Mat();
        opencv_imgproc.morphologyEx(grayimg,topHat,opencv_imgproc.MORPH_TOPHAT,element_hot);//黑帽运算
        opencv_imgproc.morphologyEx(grayimg,bottomHat,opencv_imgproc.MORPH_BLACKHAT,element_hot);//黑帽运算
        opencv_core.addWeighted(grayimg,1,bottomHat,1,0.0,tempHat);//tempHat=grayimg+bottomHat
        opencv_core.addWeighted(tempHat,1,topHat,-1,0,grayimg);//grayimg=tempHat-topHat=grayimg+bottomHat-topHat

        if(VideoInput.single)
        {
            Frame frame=matConverter.convert(grayimg);
            CanvasFrame canvasFrame=new CanvasFrame("灰度图片");
            canvasFrame.setCanvasSize(160,120);
            canvasFrame.showImage(frame);
        }
        Mat grayout=Binary(grayimg,35);//直接给定阈值二值法
        Mat grayout1=RemoveSmallRegion(grayout);
        //opencv_imgproc.morphologyEx(grayout,grayout,opencv_imgproc.MORPH_OPEN,element_open);//开运算
        //Mat grayout=EntropySeg(grayimg);//最大阈值法，自适应
        if(VideoInput.single)
        {
            Frame frame=matConverter.convert(grayout1);
            CanvasFrame canvasFrame=new CanvasFrame("二值化图片");
            canvasFrame.setCanvasSize(160,120);
            canvasFrame.showImage(frame);
        }

        //GravityCenter(grayout);

        return grayout1;
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
        opencv_imgproc.threshold(binaryimg,binaryout,value,255,opencv_imgproc.THRESH_BINARY_INV);
        return binaryout;
    }
    /**
     * 最大熵分割算法
     * @param src 待分割的原图像
     * @return 分割后图像
     */
    private Mat EntropySeg(Mat src)
    {
        int[] tbHist=new int[256];//每个像素值个数
        int index=0;//最大熵对应的灰度
        double Property=0.0;//像素所占概率
        double maxEntropy=-1.0;//最大熵
        double frontEntropy=0.0;//前景熵
        double backEntropy=0.0;//背景熵
        //纳入计算的总像素数
        int TotalPixel=0;
        int nCol=src.cols()*src.channels();//每行的像素个数
        for(int i=0;i<src.rows();i++)
        {
            BytePointer pData=src.ptr(i);
            for(int j=0;j<nCol;++j)
            {
                ++TotalPixel;
                int value=pData.get(j);
                if(value<0)
                {
                    value+=256;
                }
                tbHist[value]+=1;
            }
        }
        for(int i=0;i<256;i++)
        {
            //计算背景像素数
            double backTotal=0;
            for(int j=0;j<i;j++)
            {
                backTotal+=tbHist[j];
            }
            //背景熵
            for(int j=0;j<i;j++)
            {
                if(tbHist[j]!=0)
                {
                    Property=tbHist[j]/backTotal;
                    backEntropy+=Property*Math.log1p(Property);
                }
            }
            //前景熵
            for(int k=i;k<256;k++)
            {
                if(tbHist[k]!=0)
                {
                    Property=tbHist[k]/(TotalPixel-backTotal);
                    frontEntropy+=Property*Math.log1p(Property);
                }
            }
            //得到最大熵
            if(frontEntropy+backEntropy>maxEntropy)
            {
                maxEntropy=frontEntropy+backEntropy;
                index=i;
            }
            //清空本次计算熵值
            frontEntropy=0.0;
            backEntropy=0.0;
        }
        Mat dst=new Mat();
        index+=3;
        opencv_imgproc.threshold(src,dst,index,255,opencv_imgproc.THRESH_BINARY);
        return dst.clone();
    }
    private Mat RemoveSmallRegion(Mat src)
    {
        Mat dst=new Mat();
        src.copyTo(dst);
        IplImage srcImage=new IplImage(dst);

        CvMemStorage storage=CvMemStorage.create();
        CvSeq cvContour=new CvSeq(null);

        CvRect rect;
        double tempArea;
        opencv_imgproc.cvFindContours(srcImage,storage,cvContour,Loader.sizeof(CvContour.class),opencv_imgproc.CV_RETR_CCOMP,opencv_imgproc.CV_CHAIN_APPROX_NONE);
        dst=new Mat();
        src.copyTo(dst);
        srcImage=new IplImage(dst);
        double maxArea=0;
        CvSeq tempContour=new CvSeq(cvContour);
        while (tempContour!=null&&!tempContour.isNull())
        {
            tempArea=opencv_imgproc.cvContourArea(tempContour);
            if(tempArea>maxArea)
            {
                maxArea=tempArea;
            }
            tempContour=tempContour.h_next();
        }
        int nCol=src.cols();
        int nRow=src.rows();
        while (cvContour!=null&&!cvContour.isNull())
        {
            rect=opencv_imgproc.cvBoundingRect(cvContour);
            if(rect.x()==1||rect.x()==nCol||rect.y()==1||rect.y()==nRow)
            {
                CvPoint point=new CvPoint(rect.x()+rect.width()/2,rect.y()+rect.height()/2);
                opencv_imgproc.cvFloodFill(srcImage,point,cvblack);
            }
            tempArea=opencv_imgproc.cvContourArea(cvContour);
            if(tempArea<maxArea)
            {
                CvPoint point=new CvPoint(rect.x()+rect.width()/2,rect.y()+rect.height()/2);
                opencv_imgproc.cvFloodFill(srcImage,point,cvblack);
            }
            cvContour=cvContour.h_next();
        }
        return dst;
    }
    /**
     * 寻找图像质心
     * @param src 源图像
     * @return 质心坐标
     */
    private Box GravityCenter(Mat src)
    {
        Box center=new Box(0,0,0);
        IplImage srcImg=new IplImage(src);
        opencv_imgproc.CvMoments moments=new opencv_imgproc.CvMoments();
        opencv_imgproc.cvMoments(srcImg,moments);
        double m00=opencv_imgproc.cvGetSpatialMoment(moments,0,0);
        if(m00==0)
        {
            return center;
        }
        double m10=opencv_imgproc.cvGetSpatialMoment(moments,1,0);
        double m01=opencv_imgproc.cvGetSpatialMoment(moments,0,1);
        center.setX(m10/m00);
        center.setY(m01/m00);
        return center;
    }
    /**
     * 寻找瞳孔圆心
     * @param src 源图像
     * @param points 源图像轮廓
     * @return 瞳孔圆心
     */
    private Box CircleFit(Mat src,Vector<Point> points)
    {
        Box center=GravityCenter(src);//获取质心坐标
        Point top=new Point(0,src.cols());
        Point bottom=new Point(0,0);
        Point right=new Point(0,0);
        Point left=new Point(src.cols(),0);
        long sum=points.size();
        int x,y;
        for(int i=0;i<sum;i++)
        {
            //取得四个顶点
            x=points.get(i).x();
            y=points.get(i).y();
            if(x<left.x())
            {
                //左侧
                left=points.get(i);
            }
            if(x>right.x())
            {
                right=points.get(i);
            }
            if(y<top.y())
            {
                top=points.get(i);
            }
            if(y>bottom.y())
            {
                bottom=points.get(i);
            }
        }
        double width=bottom.y()-top.y();
        double length=right.x()-left.x();
        if(length/width>1.15)
        {
            //眼皮遮挡眼球一部分
            double R=(right.x()-left.x()+(bottom.y()-left.y()))/3.0;
            double Y=(left.y()+bottom.y()-R)/2.0;
            center.setY(Y);
            center.setR(R);
        }
        else
        {
            //近似圆
            Box fitCenter=circleLeastFit(points);
            center.setR(fitCenter.getR());
        }
        return center;
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
        CvSeq cvLcontour=new CvSeq(null);//检测的整个大轮廓
        CvSeq cvtempLcontour=new CvSeq(null);//临时轮廓
        CvSeq cvLcontourKeep=new CvSeq(null);//需要绘制的轮廓


        Lgrayimg=GrayDetect(Leye);

        if(VideoInput.IsSaveImage&&VideoInput.IsSaveImageSingle&&Main.frameHash.contains(VideoInput.frameNum))
        {
            BufferedImage bufferedImageSrc=frameConverter.convert(matConverter.convert(Leye));
            BufferedImage bufferedImageDst=frameConverter.convert(matConverter.convert(Lgrayimg));
            try
            {
                ImageIO.write(bufferedImageSrc,"bmp",new File(VideoInput.srcSaveImageFile+"\\"+VideoInput.currentVideoName+VideoInput.frameNum+".bmp"));
                ImageIO.write(bufferedImageDst,"bmp",new File(VideoInput.dstSaveImageFile+"\\"+VideoInput.currentVideoName+VideoInput.frameNum+".bmp"));
            }
            catch (IOException e)
            {
                e.printStackTrace();//在命令行打印异常信息在程序中出错的位置及原因
            }
        }

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
                cvLcontourKeep=new CvSeq(cvtempLcontour);
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
                    //Box Lbox=circleLeastFit(leftPoints);//左眼拟合圆检测
                    Box Lbox=CircleFit(Lgrayimg,leftPoints);
                    if(Lbox.getX()!=0&&Lbox.getY()!=0)
                    {
                        Lcircles.add(Lbox);
                    }
                    if(Lbox.getR()!=0)
                    {
                        //如果半径不为0
                        //Lcircles.add(Lbox);
                    }
                }
            }
        }
        //绘制圆、十字标
        if(Lcircles.size()>0)
        {
            PlotC(Lcircles,LeyeImage);
        }
        //绘制轮廓
        opencv_imgproc.cvDrawContours(LeyeImage,cvLcontourKeep,cvgreen,cvgreen,1);
        Leye=new Mat(LeyeImage);
    }
    public Iterable<Box> Lcircles()
    {
        return Lcircles;
    }
}
