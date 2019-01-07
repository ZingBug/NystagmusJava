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
import java.io.*;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Created by ZingBug on 2017/10/11.
 */
public class VideoInput implements Consumer<Map<String,WaveChart>> {
    public static boolean single=false;//是否为单张照片
    private static OpenCVFrameConverter.ToIplImage matConverter = new OpenCVFrameConverter.ToIplImage();//Mat转Frame
    public static String dstSaveImageFile;
    public static String srcSaveImageFile;

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

    //锁
    private static Lock lock=new ReentrantLock();

    //像素转实际距离单位
    private static double pixel2mm=0.3;

    //输出文件
    private static String saveText;
    private static String saveTextSPV;
    private static FileWriter fw;
    private static FileWriter fwSPV;
    private static int saveFrameNum=0;
    private DecimalFormat df=new DecimalFormat("##.##");//数据格式,float转string保留2位小数


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

        if(GlobalValue.isSaveXdata)
        {
            //保存帧数
            saveText=GlobalValue.saveDataPath+"/point/"+GlobalValue.saveNumber+".txt";
            saveTextSPV=GlobalValue.saveDataPath+"/spv/"+GlobalValue.saveNumber+".txt";
            try
            {
                fw=new FileWriter(saveText);//覆盖模式
                fwSPV=new FileWriter(saveTextSPV,true);//追加模式
                saveFrameNum=0;
            }
            catch (IOException e)
            {
                System.out.println(e.toString());
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

        STOP=false;

        timer.schedule(new readFrame(),50,10);//执行多次
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

                        if(GlobalValue.isSaveXdata)
                        {
                            closeText();
                        }
                    }
                    System.out.println("视频播放结束");
                    timer.cancel();
                    return;
                }
            }
            catch (Exception e)
            {
                System.out.println("视频播放结束 "+e.toString());
                if(GlobalValue.isSaveXdata)
                {
                    closeText();
                }
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
            Box box;
            if(process.containCenter())
            {
                box=process.getCenter();
                box.setX(LeftFrameMat.cols()-box.getX());//翻转x轴
                //先滤波处理

                filterX.add(box);
                box=filterX.get();

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
                if(!IsLeyeCenter&&!single)
                {
                    IsLeyeCenter=true;
                    LeyeCenter.setX(box.getX());
                    LeyeCenter.setY(box.getY());
                }
                else if(!single)
                {
                    //waveChart_position.add(frameNum,box.getX());
                    //不做滤波处理
                    waveChart_position.add(frameNum/50.0,box.getX()-LeyeCenter.getX());

                    //旋转角度SPV
                    double diffX=box.getX()-preBox.getX();
                    double diffY=box.getY()-preBox.getY();
                    waveChart_rotation.add(frameNum,Math.atan(diffY/diffX));

                    if(GlobalValue.isSaveXdata&&(frameNum>=GlobalValue.saveStartFrameNumber)&&(saveFrameNum<GlobalValue.saveFrameNumber))
                    {
                        //outText(frameNum+"   "+(box.getX()-LeyeCenter.getX())*pixel2mm);
                        outText(""+box.getX());
                        saveFrameNum++;
                    }
                    //calculate.addEyeX(box.getX());
                    if((frameNum/TimerSecondNum>=60)&&(frameNum/TimerSecondNum<=80))
                    {
                        calculate.addEyeX(box.getX());
                    }
                }
                preBox=box;
            }

            if(!single&&(frameNum/TimerSecondNum)>81)
            {
                secondNum++;
                calculate.processEyeX(secondNum);
                double realSPVX=calculate.getSPV(secondNum);
                System.out.println("第 "+secondNum*10+" s : "+df.format(realSPVX));
                timer.cancel();
            }
            /*
            if(!single&&frameNum%(TimerSecondNum*10)==0)
            {
                secondNum++;
                calculate.processEyeX(secondNum);
                double realSPVX=calculate.getSPV(secondNum);
                System.out.println("第 "+secondNum*10+" s : "+df.format(realSPVX));
            }
            */
            //后续显示
            LeftFrame=matConverter.convert(Leye);//左眼
            canvas.showImage(LeftFrame);
        }
        private double distance(Box x,Box y)
        {
            //求两点之间绝对距离
            return Math.sqrt(Math.pow(x.getX()-y.getX(),2)+Math.pow(x.getY()-y.getY(),2));
        }

        private void outText(String message)
        {
            try
            {
                fw.write(message+System.getProperty("line.separator"));
            }
            catch (IOException e)
            {
                System.out.println(e.toString());
            }

        }
        private void outTextSPV(String message)
        {
            try
            {
                fwSPV.write(message+System.getProperty("line.separator"));
            }
            catch (IOException e)
            {
                System.out.println(e.toString());
            }

        }

        private void closeText()
        {
            try
            {
                fw.close();
                fwSPV.close();
            }
            catch (IOException e)
            {
                System.out.println(e.toString());
            }
        }
    }

}