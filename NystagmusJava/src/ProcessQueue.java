import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.util.Queue;
import java.util.concurrent.*;

public class ProcessQueue {
    private static FFmpegFrameGrabber capture;
    private static String videoPath="F:\\GitHub\\NystagmusJava\\NystagmusJava\\睁眼.avi";
    //private static String videoPath="D:\\BaiduNetdiskDownload\\EP35.mp4";
    private static CanvasFrame canvas;

    //队列
    private static final int queueSize=100;
    private BlockingDeque<Frame> frameQueue=new LinkedBlockingDeque<>(queueSize);//阻塞队列

    private static boolean STOP=false;


    public void start()
    {
        capture=new FFmpegFrameGrabber(videoPath);
        try
        {
            capture.start();
            System.out.println("视频加载成功");
            double rate=capture.getFrameRate();
            System.out.println("帧速率："+rate);
            canvas=new CanvasFrame("显示");
            int delay=(int)(1000/rate);
            Thread readImageThread=new ReadImageThread(capture);
            Thread processImageThread=new ProcessImageThread(delay);
            readImageThread.start();
            processImageThread.start();

        }
        catch (FrameGrabber.Exception e)
        {
            System.out.println("视频加载失败");
            System.out.println(e.toString());
        }

    }

    private class ReadImageThread extends Thread
    {
        private FFmpegFrameGrabber grabber;//视频
        private double rate;//帧速率
        private int delay;//延迟时间
        private boolean stop;
        private int num;
        private ReadImageThread(FFmpegFrameGrabber grabber)
        {
            this.grabber=grabber;
            this.rate=grabber.getFrameRate();
            this.delay=(int)(500/rate);
            this.stop=false;
            num=0;
        }
        public void setStop(boolean stop)
        {
            this.stop=stop;
        }
        @Override
        public void run() {
            while (!stop)
            {
                try
                {
                    Frame frame=grabber.grabFrame();
                    if(frame==null)
                    {
                        System.out.println("读取函数结束"+num);
                        STOP=true;
                        break;
                    }
                    frameQueue.put(frame);//往队列中添加图像

                    num++;
                    Thread.sleep(delay);
                }
                catch (FrameGrabber.Exception e)
                {
                    System.out.println("视频读取出现问题");
                    System.out.println(e.toString());
                    break;
                }
                catch (InterruptedException e)
                {
                    System.out.println("视频读取时延迟出现问题");
                    System.out.println(e.toString());
                }
            }
        }
    }

    private class ProcessImageThread extends Thread
    {
        private boolean stop;
        private int delay;
        //private CanvasFrame canvas;
        private int num;
        private long startTime;
        private long endTime;

        private ProcessImageThread(int delay)
        {
            this.stop=false;
            this.delay=delay;
            //canvas=new CanvasFrame("显示");
            this.num=0;
        }
        @Override
        public void run() {
            startTime=System.currentTimeMillis();
            while (!stop)
            {
                try
                {
                    if(STOP&&frameQueue.size()==0)
                    {
                        System.out.println("处理函数结束"+num);
                        endTime=System.currentTimeMillis();
                        System.out.println("时间："+(endTime-startTime));
                        break;
                    }
                    Frame frame=frameQueue.poll(500L,TimeUnit.MILLISECONDS);//取出来并删除
                    if(frame==null)
                    {
                        continue;
                    }
                    canvas.showImage(frame);
                    num++;
                    //Thread.sleep(delay);
                }
                catch (InterruptedException e)
                {
                    System.out.println("图像处理出现问题"+num);
                    System.out.println(e.toString());
                }
                catch (IllegalArgumentException e)
                {
                    System.out.println("图像显示出现问题"+num);
                    System.out.println(e.toString());
                }
            }
        }
    }

    public static void main(String[] args)
    {
        ProcessQueue processQueue=new ProcessQueue();
        processQueue.start();
    }
}
