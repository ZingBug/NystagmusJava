import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.*;
import org.bytedeco.javacpp.opencv_core.*;

import javax.swing.*;
import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by ZingBug on 2017/10/11.
 */
public class VideoInput {
    public static boolean single=false;
    private static OpenCVFrameConverter.ToIplImage matConverter = new OpenCVFrameConverter.ToIplImage();//Mat转Frame
    public static String dstSaveImageFile="C:\\dst";
    public static String srcSaveImageFile="C:\\src";
    public static int frameNum=0;
    public static boolean IsSaveImage=true;//是否选择保存图像
    public static boolean IsSaveImageSingle=false;//是否保存当前文件图像

    private FFmpegFrameGrabber capture;//视频打开引擎
    private Timer timer;//定时器

    /*图像*/
    private Mat AllEyeMat;//双眼图像
    private Frame AllFrame;
    private Mat LeftFrameMat;
    private Mat RightFrameMat;
    private Frame LeftFrame;
    private Frame RightFrame;
    private Mat Leye;//输出左眼图像
    private Mat Reye;//输出右眼图像

    /*与上一帧比较*/
    private Box preBox;

    /*显示窗口*/
    private CanvasFrame canvas;

    public VideoInput(String VideoPath)
    {
        if(IsSaveImage)
        {
            //如果存在文件夹
            JFileChooser chooser=new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("选择照片保存路径");
            int result=chooser.showOpenDialog(null);
            if(result==JFileChooser.APPROVE_OPTION)
            {
                String filePath=chooser.getSelectedFile().getPath();
                srcSaveImageFile=filePath+"\\src";
                dstSaveImageFile=filePath+"\\dst";
                File src=new File(srcSaveImageFile);
                File dst=new File(dstSaveImageFile);
                if(!creatFile(src)||!creatFile(dst))
                {
                    System.out.println("照片保存文件夹创建失败");
                    return;
                }
                IsSaveImageSingle=true;
            }
            else
            {
                IsSaveImageSingle=false;
            }
        }

        if(VideoPath.contains("jpg"))
        {
            //单张照片
            single=true;
        }
        else
        {
            single=false;
        }
        capture=new FFmpegFrameGrabber(VideoPath);
        try
        {
            capture.start();
        }
        catch (FrameGrabber.Exception e1)
        {
            //播放失败
            System.out.println("视频加载失败 "+e1.toString());
            return;
        }
        System.out.println("视频加载成功 "+VideoPath);
        timer=new Timer();
        canvas=new CanvasFrame("左眼显示");
        canvas.setCanvasSize(160,120);
        frameNum=0;
        timer.schedule(new readFrame(),50,10);//执行多次
        //timer.schedule(new readFrame(),50);//执行一次

    }
    /*判断文件夹是否存在，不存在则创建*/
    private boolean creatFile(File file)
    {
        if(file.exists())
        {
            if(file.isDirectory())
            {
                //存在文件夹
                return true;
            }
            else
            {
                //存在文件，而不是文件夹
                file.delete();
                return file.mkdir();
            }
        }
        else
        {
            return file.mkdir();
        }
    }
    class readFrame extends TimerTask
    {
        @Override
        public void run()
        {
            AllFrame=new Frame();
            try
            {
                AllFrame=capture.grabFrame();
                if(AllFrame==null)
                {
                    System.out.println("视频播放结束");
                    timer.cancel();
                    return;
                }
            }
            catch (Exception e)
            {
                System.out.println("视频播放结束 "+e.toString());
                timer.cancel();
                return;
            }
            //图像切割
            AllEyeMat=matConverter.convertToMat(AllFrame);
            Rect reye_box = new Rect(0, 1, AllEyeMat.cols()/2, AllEyeMat.rows() - 1);
            Rect leye_box = new Rect(AllEyeMat.cols()/2, 1, AllEyeMat.cols()/2-1, AllEyeMat.rows() - 1);
            LeftFrameMat=new Mat(AllEyeMat,reye_box);//左眼
            RightFrameMat=new Mat(AllEyeMat,leye_box);//右眼

            /*图像处理程序*/
            frameNum++;
            ImgProcess process=new ImgProcess();
            process.Start(LeftFrameMat,1.8);
            //process.Start(AllEyeMat,1.8);
            process.ProcessSeparate();
            Leye=process.OutLeye();
            for(Box box:process.Lcircles())
            {
                //圆心坐标
                if(preBox==null)
                {
                    preBox=box;
                    break;
                }
                if(distance(box,preBox)>(box.getR()+preBox.getR()/1.5)&&(Math.abs(box.getR()-preBox.getR())>box.getR()/2.0))
                {
                    //与上一帧做对比
                    return;
                }
                preBox=box;
            }
            //后续显示
            LeftFrame=matConverter.convert(Leye);//左眼
            canvas.showImage(LeftFrame);
        }
        private double distance(Box x,Box y)
        {
            //求两点之间绝对距离
            return Math.sqrt(Math.pow(x.getX()-y.getX(),2)+Math.pow(x.getY()-y.getY(),2));
        }
    }
}
