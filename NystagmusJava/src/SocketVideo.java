import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class SocketVideo {
    private static FFmpegFrameGrabber capture;
    private static String videoPath="F:\\GitHub\\NystagmusJava\\NystagmusJava\\睁眼.avi";
    //private static String videoPath="D:\\BaiduNetdiskDownload\\EP35.mp4";
    private static CanvasFrame canvas;
    private static OpenCVFrameConverter.ToIplImage converter=new OpenCVFrameConverter.ToIplImage();

    //队列
    private static final int queueSize=100;//阻塞队列容量
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
            //Thread sendImageThread=new SendImageThread();
            readImageThread.start();
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

    private class SendImageThread extends Thread
    {
        private String ip;
        private int port;
        private Socket socket;
        private OutputStream outsocket;
        private boolean stop;
        private byte byteBuffer[] =new byte[10000];

        private SendImageThread(String ip,int port)
        {
            this.ip=ip;
            this.port=port;
        }

        @Override
        public void run() {
            try
            {
                socket=new Socket(ip,port);
                outsocket=socket.getOutputStream();
                while (!stop)
                {
                    try
                    {
                        if(STOP&&frameQueue.size()==0)
                        {
                            System.out.println("发送结束");
                            break;
                        }
                        Frame frame=frameQueue.poll(500L, TimeUnit.MILLISECONDS);
                        opencv_core.IplImage image=converter.convertToIplImage(frame);
                        BytePointer pointer=image.imageData();
                        byteBuffer=pointer.getStringBytes();

                        if(frame==null)
                        {
                            continue;
                        }
                    }
                    catch (InterruptedException e)
                    {
                        System.out.println("图像处理出现问题");
                    }
                }

            }
            catch (IOException e)
            {
                System.out.println(e.toString());
            }
        }
    }

    public static void main(String[] args)
    {
        SocketVideo socketVideo=new SocketVideo();
        socketVideo.start();
    }
}
