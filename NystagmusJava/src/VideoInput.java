import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.*;
import org.bytedeco.javacpp.opencv_core.*;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by ZingBug on 2017/10/11.
 */
public class VideoInput {
    private static OpenCVFrameConverter.ToIplImage matConverter = new OpenCVFrameConverter.ToIplImage();//Mat转Frame

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

    /*显示窗口*/
    private CanvasFrame canvas;

    public VideoInput(String VideoPath)
    {
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
        canvas.setCanvasSize(400,300);
        timer.schedule(new readFrame(),50,10);

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
            ImgProcess process=new ImgProcess();
            process.Start(LeftFrameMat,1.8);
            process.ProcessSeparate();
            Leye=process.OutLeye();

            //后续显示
            LeftFrame=matConverter.convert(Leye);//左眼
            canvas.showImage(LeftFrame);

        }
    }
}
