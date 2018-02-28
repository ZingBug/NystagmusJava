import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;

import java.io.IOException;

/**
 * Created by ZingBug on 2018/2/3.
 */
public class Test {

    private static FFmpegFrameGrabber capture;
    private static String videoPath="F:\\GitHub\\NystagmusJava\\NystagmusJava\\睁眼.avi";
    //private static String videoPath="D:\\BaiduNetdiskDownload\\EP35.mp4";
    private static CanvasFrame canvas;

    public static void main(String[] args)
    {
        capture=new FFmpegFrameGrabber(videoPath);
        try
        {
            capture.start();
        }
        catch (FrameGrabber.Exception e)
        {
            System.out.println("加载失败");
            System.out.println(e.toString());
            return;
        }
        System.out.println("加载成功");
        double rate=capture.getFrameRate();
        //System.out.println(capture.getVideoBitrate());
        System.out.println("帧速率："+rate);
        //System.out.println(capture.getVideoCodec());
        canvas=new CanvasFrame("显示");
        int delay=(int)(1000/rate);
        boolean stop=false;
        int num=0;
        long startTime=System.currentTimeMillis();//获取开始时间
        while (!stop)
        {
            try {
                Frame frame=capture.grabFrame();
                if(frame==null)
                {
                    stop=true;
                }
                else {
                    num++;
                    canvas.showImage(frame);
                    Thread.sleep(delay);
                }
            }
            catch (FrameGrabber.Exception e)
            {
                System.out.println("播放中出现问题");
                System.out.println(e.toString());
            }
            catch (InterruptedException e)
            {
                System.out.println("线程延迟出现问题");
                System.out.println(e.toString());
            }
        }
        long endTime=System.currentTimeMillis();//获取结束时间
        System.out.println("播放结束");
        System.out.println(num);
        System.out.println("播放时间为："+(endTime-startTime)+"ms");
    }
}
