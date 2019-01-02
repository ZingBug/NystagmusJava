import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.*;

import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Timer;


/**
 * Created by ZingBug on 2018/12/28.
 */
public class VideosInput {

    private static OpenCVFrameConverter.ToIplImage matConverter = new OpenCVFrameConverter.ToIplImage();//Mat转Frame

    private static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式

    public static int frameNum=0;

    private FFmpegFrameGrabber capture;//视频打开引擎
    private Timer timer;//定时器

    /*图像*/
    private opencv_core.Mat AllEyeMat;//双眼图像
    private Frame AllFrame;
    private opencv_core.Mat LeftFrameMat;
    private opencv_core.Mat RightFrameMat;


    /*与上一帧比较*/
    private Box preBox;

    //输出文件
    private static FileWriter fwX;
    private static FileWriter fwY;

    private Object object=new Object();

    private boolean stop=true;


    public VideosInput(String dirPath)
    {
        ArrayList<File> list=getAllVideo(dirPath);

        for(File file:list)
        {
            if(!file.isFile())
            {
                continue;
            }
            String path=file.getPath();
            String name=getFileNameNoEx(file.getName());

            String pointPathX=GlobalValue.saveDataPath+"/point/x/"+name+".txt";
            String pointPathY=GlobalValue.saveDataPath+"/point/y/"+name+".txt";

            try
            {
                fwX=new FileWriter(pointPathX);
                fwY=new FileWriter(pointPathY);
            }
            catch (IOException e)
            {
                System.out.println(e.toString());
            }

            capture=new FFmpegFrameGrabber(path);
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
            System.out.println("视频加载成功 "+path);

            timer=new Timer();
            stop=true;

            timer.schedule(new VideosInput.readFrame(),50,10);//执行多次

            while (stop)
            {
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("所有视频处理结束！！！");

    }

    private String getFileNameNoEx(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot >-1) && (dot < (filename.length()))) {
                return filename.substring(0, dot);
            }
        }
        return filename;
    }


    private ArrayList<File> getAllVideo(String dirPath)
    {
        ArrayList<File> list=new ArrayList<>();
        File file=new File(dirPath);
        if(file.isDirectory())
        {
            File[] files=file.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    String name=pathname.getName();
                    if(name.endsWith(".avi")||name.endsWith(".mp4"))
                    {
                        return true;
                    }
                    return false;
                }
            });
            for(File file1:files)
            {
                if(file1.isFile())
                {
                    list.add(file1);
                }
            }
        }

        return list;
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
                    capture.release();
                    closeText();
                    System.out.println(df.format(new Date())+" 视频处理结束");
                    timer.cancel();
                    stop=false;
                    return;
                }
            }
            catch (Exception e)
            {
                System.out.println("视频处理结束 "+e.toString());
                closeText();
                timer.cancel();
                stop=false;
                return;
            }
            //图像切割
            AllEyeMat=matConverter.convertToMat(AllFrame);

            opencv_core.Rect reye_box = new opencv_core.Rect(0, 1, AllEyeMat.cols()/2, AllEyeMat.rows() - 1);
            opencv_core.Rect leye_box = new opencv_core.Rect(AllEyeMat.cols()/2, 1, AllEyeMat.cols()/2-1, AllEyeMat.rows() - 1);
            LeftFrameMat=new opencv_core.Mat(AllEyeMat,reye_box);//左眼
            RightFrameMat=new opencv_core.Mat(AllEyeMat,leye_box);//右眼


            /*图像处理程序*/
            ImgProcess process=new ImgProcess();
            process.Start(LeftFrameMat,1.8);
            process.ProcessSeparate();

            process.OutLeye();
            Box box;
            if(process.containCenter())
            {
                box=process.getCenter();
                box.setX(LeftFrameMat.cols()-box.getX());//翻转x轴

                //圆心坐标
                if(preBox==null)
                {
                    preBox=box;
                }
            }
            else
            {
                //闭眼情况
                box=preBox;
            }
            if(box!=null)
            {

                outTextX(""+box.getX());
                outTextY(""+box.getY());
                preBox=box;
            }
        }


        private void outTextX(String message)
        {
            try
            {
                fwX.write(message+System.getProperty("line.separator"));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

        }
        private void outTextY(String message)
        {
            try
            {
                fwY.write(message+System.getProperty("line.separator"));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

        }

        private void closeText()
        {
            try
            {
                fwX.close();
                fwY.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

}
