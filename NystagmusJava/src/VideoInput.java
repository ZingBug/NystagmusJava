import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.*;
import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacv.Frame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Created by ZingBug on 2017/10/11.
 */
public class VideoInput implements Consumer<Map<String,WaveChart>> {
    public static boolean single=false;//是否为单张照片
    private static OpenCVFrameConverter.ToIplImage matConverter = new OpenCVFrameConverter.ToIplImage();//Mat转Frame
    public static String dstSaveImageFile="C:\\dst";
    public static String srcSaveImageFile="C:\\src";
    public static int frameNum=0;
    public static boolean IsSaveImage=false;//是否选择保存图像
    public static boolean IsSaveImageSingle=false;//是否保存当前文件图像
    public static String currentVideoNamePinYin="";

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
    private Box LeyeCenter;
    private boolean IsLeyeCenter;

    /*波形图*/
    private WaveChart waveChart_position;//波形显示 坐标
    private WaveChart waveChart_rotation;//旋转SPV
    private PointFilter filterX;//滤波

    /*显示窗口*/
    private CanvasFrame canvas;//显示图像窗口

    /*眼震信号特征*/
    private Calculate calculate;//计算
    private final int TimerSecondNum=50;//每秒帧数
    private int secondNum=0;//秒数

    //阻塞队列
    private final int queueSize=100;
    private BlockingDeque<Frame> frameQueue=new LinkedBlockingDeque<>(queueSize);

    //信号量
    private static boolean STOP=false;

    //是否为在线视频
    private boolean isOnline=false;

    public VideoInput(String VideoPath,boolean isOnline)
    {
        this.isOnline=isOnline;
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

        if((!isOnline)&&VideoPath.contains("jpg"))
        {
            //单张照片
            single=true;
        }
        else
        {
            single=false;
            filterX=new PointFilter();//滤波
            calculate=new Calculate();//计算
        }
        //F:\GitHub\NystagmusJava\NystagmusJava\眼线遮挡.avi
        File file=new File(VideoPath);
        String tempName=file.getName();
        String currentVideoName=tempName.substring(0,tempName.lastIndexOf("."));
        currentVideoNamePinYin=ToPinyin(currentVideoName);

        IsLeyeCenter=false;
        LeyeCenter=new Box(0,0,0);

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
        //增加关闭监听
        canvas.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                timer.cancel();
            }
        });

        frameNum=0;
        secondNum=0;


        double rate=capture.getFrameRate();
        Thread readImageThread=new ReadImageThread(capture);
        Thread processImageThread=new ProcessImageThread(rate);
        STOP=false;
        readImageThread.start();
        processImageThread.start();


        //timer.schedule(new readFrame(),50,10);//执行多次
        //timer.schedule(new readFrame(),50);//执行一次

    }
    @Override
    public void accept(Map<String,WaveChart> map) throws NullPointerException
    {
        waveChart_position=map.get("position");
        waveChart_rotation=map.get("rotation");
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
    /**
     * 汉字转为拼音
     * @param chinese
     * @return
     */
    public static String ToPinyin(String chinese){
        String pinyinStr = "";
        char[] newChar = chinese.toCharArray();
        HanyuPinyinOutputFormat defaultFormat = new HanyuPinyinOutputFormat();
        defaultFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        defaultFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        for (int i = 0; i < newChar.length; i++) {
            if (newChar[i] > 128) {
                try {
                    pinyinStr += PinyinHelper.toHanyuPinyinStringArray(newChar[i], defaultFormat)[0];
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    e.printStackTrace();
                }
            }else{
                pinyinStr += newChar[i];
            }
        }
        return pinyinStr;
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
                    if(!single)
                    {
                        secondNum++;
                        calculate.processLeyeX(secondNum);
                        System.out.println("第 "+secondNum+" s : "+calculate.getSecondSPV(secondNum));
                        System.out.println("眼震方向为: "+(calculate.judgeFastPhase()?"左向":"右向"));
                    }
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
                //先滤波处理
                //filterX.add(box);
                //box=filterX.get();

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
                if(!IsLeyeCenter&&!single)
                {
                    IsLeyeCenter=true;
                    LeyeCenter.setX(box.getX());
                    LeyeCenter.setY(box.getY());
                }
                else if(!single)
                {
                    calculate.addLeyeX(box.getX());
                    //waveChart_position.add(frameNum,box.getX());
                    //不做滤波处理
                    waveChart_position.add(frameNum/50.0,box.getX()-LeyeCenter.getX());

                    //旋转角度SPV
                    double diffX=box.getX()-preBox.getX();
                    double diffY=box.getY()-preBox.getY();
                    waveChart_rotation.add(frameNum,Math.atan(diffY/diffX));
                }
                preBox=box;
            }
            if(!single&&frameNum%TimerSecondNum==0)
            {
                secondNum++;
                calculate.processLeyeX(secondNum);
                System.out.println("第 "+secondNum+" s : "+calculate.getSecondSPV(secondNum));
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

    private class ReadImageThread extends Thread
    {
        private FFmpegFrameGrabber grabber;//视频
        private double rate;//帧速率
        private int delay;//延迟时间
        private boolean stop;
        private int frameNum;
        private ReadImageThread(FFmpegFrameGrabber grabber)
        {
            this.grabber=grabber;
            this.rate=grabber.getFrameRate();
            this.delay=(int)(1000/rate);
            this.stop=false;
            this.frameNum=0;
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
                        System.out.println("读取函数结束"+this.frameNum);
                        STOP=true;
                        break;
                    }
                    frameQueue.put(frame);//往队列中添加图像

                    this.frameNum++;
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
        private int delay;//延迟
        //private CanvasFrame canvas;
        private int frameNum;//帧数
        private double rate;//帧率
        private long startTime;
        private long endTime;

        private ProcessImageThread(double rate)
        {
            this.stop=false;
            this.rate=rate;
            this.delay=(int)(1000/rate);
            //canvas=new CanvasFrame("显示");
            this.frameNum=0;
        }
        @Override
        public void run() {
            //startTime=System.currentTimeMillis();
            while (!stop)
            {
                try
                {
                    if(STOP&&frameQueue.size()==0)
                    {
                        if(!single)
                        {
                            System.out.println("处理图像结束："+this.frameNum);
                            secondNum++;
                            calculate.processLeyeX(secondNum);
                            System.out.println("第 "+secondNum+" s : "+calculate.getSecondSPV(secondNum));
                            System.out.println("眼震方向为: "+(calculate.judgeFastPhase()?"左向":"右向"));
                        }
                        break;
                    }
                    //AllFrame=new Frame();

                    AllFrame=frameQueue.poll(500L, TimeUnit.MILLISECONDS);//取出来并删除

                    if(AllFrame==null)
                    {
                        continue;
                    }
                    AllEyeMat=matConverter.convertToMat(AllFrame);
                    if(AllEyeMat==null)
                    {
                        continue;
                    }

                    if(isOnline)
                    {
                        //如果是在线视频
                        LeftFrameMat=AllEyeMat.clone();
                    }
                    else
                    {
                        //本地视频，需要图像切割
                        Rect reye_box = new Rect(0, 1, AllEyeMat.cols()/2, AllEyeMat.rows() - 1);
                        Rect leye_box = new Rect(AllEyeMat.cols()/2, 1, AllEyeMat.cols()/2-1, AllEyeMat.rows() - 1);
                        LeftFrameMat=new Mat(AllEyeMat,reye_box);//左眼
                        RightFrameMat=new Mat(AllEyeMat,leye_box);//右眼
                    }

                    this.frameNum++;

                    /*图像处理程序*/
                    ImgProcess process=new ImgProcess();
                    process.Start(LeftFrameMat,1.8);
                    process.ProcessSeparate();

                    Leye=process.OutLeye();
                    for(Box box:process.Lcircles())
                    {
                        //先滤波处理
                        filterX.add(box);
                        box=filterX.get();

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
                        if(!IsLeyeCenter&&!single)
                        {
                            IsLeyeCenter=true;
                            LeyeCenter.setX(box.getX());
                            LeyeCenter.setY(box.getY());
                        }
                        else if(!single)
                        {
                            calculate.addLeyeX(box.getX());
                            //waveChart_position.add(frameNum,box.getX());
                            //不做滤波处理
                            waveChart_position.add(frameNum/this.rate,box.getX()-LeyeCenter.getX());

                            //旋转角度SPV
                            double diffX=box.getX()-preBox.getX();
                            double diffY=box.getY()-preBox.getY();
                            waveChart_rotation.add(frameNum,Math.atan(diffY/diffX));
                        }
                        preBox=box;
                    }
                    if(!single&&frameNum%this.rate==0)
                    {
                        secondNum++;
                        calculate.processLeyeX(secondNum);
                        System.out.println("第 "+secondNum+" s : "+calculate.getSecondSPV(secondNum));
                    }
                    //后续显示
                    LeftFrame=matConverter.convert(Leye);//左眼
                    canvas.showImage(LeftFrame);

                    //Thread.sleep(delay);
                }
                catch (InterruptedException e)
                {
                    System.out.println("图像处理出现问题"+frameNum);
                    System.out.println(e.toString());
                }
                catch (IllegalArgumentException e)
                {
                    System.out.println("图像显示出现问题"+frameNum);
                    System.out.println(e.toString());
                }
            }
        }
        private double distance(Box x,Box y)
        {
            //求两点之间距离
            return Math.sqrt(Math.pow(x.getX()-y.getX(),2)+Math.pow(x.getY()-y.getY(),2));
        }
    }
}