
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Created by ZingBug on 2017/10/11.
 */
public class Main {
    public static HashSet<Integer> frameHash=new HashSet<>();
    private static final String name="F:\\GitHub\\NystagmusJava\\NystagmusJava\\1.jpg";
    private static final String textName="config.txt";
    public static String fileName="";

    public static void main(String[] args)
    {
        File courseFile=new File("");
        try
        {
            fileName=courseFile.getCanonicalPath()+("\\"+textName);
            File file=new File(fileName);
            if(file.exists())
            {
                //存在的话
                FileInputStream fis=new FileInputStream(file);
                Scanner in=new Scanner(new BufferedInputStream(fis),"UTF-8");
                if(in.hasNextLine())
                {
                    saveFrame(in.nextLine());
                }
            }

        }catch (IOException e)
        {
            e.printStackTrace();
        }

        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                ImageViewerFrame frame=new ImageViewerFrame();
                //WaveChart waveChart_position=new WaveChart("points","Nystagmus Waveform","Coordinate");
                //WaveChart waveChart_position=new WaveChart("","","Horizontal Coordinate");
                WaveChart waveChart_position=new WaveChart("","","Horizontal Coordinate/mm");
                WaveChart waveChart_rotation=new WaveChart("X轴","旋转SPV","角度");
                frame.getContentPane().add(waveChart_position,new BorderLayout().NORTH);
                frame.getContentPane().add(waveChart_rotation,new BorderLayout().SOUTH);
                Map<String,WaveChart> map=new HashMap<>();
                map.put("position",waveChart_position);
                map.put("rotation",waveChart_rotation);
                frame.accept(map);

                //(new Thread(waveChart)).start();
                frame.pack();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
            }
        });
    }
    public static int saveFrame(String str)
    {
        frameHash.clear();
        if(str.isEmpty()||str.length()==0)
        {
            return -1;
        }
        String[] frames=str.split(",");
        for(int i=0;i<frames.length;i++)
        {
            try
            {
                int frame=Integer.parseInt(frames[i]);
                if(frame<=0)
                {
                    return -1;
                }
                frameHash.add(frame);
            }
            catch (NumberFormatException e1)
            {
                return -1;
            }
        }
        return 0;
    }
}
class ImageViewerFrame extends JFrame implements Consumer<Map<String,WaveChart>>{
    private JLabel label;
    private JFileChooser chooser;
    private static final int DEFAULT_WIDTH=300;
    private static final int DEFAULT_HEIGHT=400;
    private Map<String,WaveChart> map;

    private static final String onlineAddress="http://192.168.43.119:8080/?action=stream?dummy=param.mjpg";

    public ImageViewerFrame()
    {
        setTitle("ImageViewer");
        setSize(DEFAULT_WIDTH,DEFAULT_HEIGHT);
        label=new JLabel();
        add(label);
        chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File("."));
        JMenuBar menubar = new JMenuBar();
        setJMenuBar(menubar);
        JMenu menu = new JMenu("File");
        menubar.add(menu);
        JMenuItem openItem = new JMenuItem("Open");
        menu.add(openItem);
        JMenuItem exitItem = new JMenuItem("Close");
        menu.add(exitItem);
        JMenuItem onlineItem=new JMenuItem("Online");
        menu.add(onlineItem);
        JMenuItem setItem=new JMenuItem("Set");
        menu.add(setItem);
        openItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int result=chooser.showOpenDialog(null);
                if(result==JFileChooser.APPROVE_OPTION)
                {
                    /*读取到的视频路径*/
                    String VideoPath=chooser.getSelectedFile().getPath();
                    VideoInput videoInput=new VideoInput(VideoPath,false);
                    for(WaveChart waveChart:map.values())
                    {
                        waveChart.clear();
                    }
                    videoInput.accept(map);
                }
            }
        });
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        setItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String inputValue=JOptionPane.showInputDialog("请输入视频保存帧数，多帧时请用英文逗号隔开");
                if(inputValue!=null)
                {
                    if(Main.saveFrame(inputValue)==0)
                    {
                        //是标准规范
                        File file=new File(Main.fileName);
                        if(!file.exists())
                        {
                            //不存在文件就新建
                            try
                            {
                                file.createNewFile();
                            }catch (IOException e1)
                            {
                                e1.printStackTrace();
                            }
                        }
                        try
                        {
                            FileOutputStream out=new FileOutputStream(file,false);
                            out.write(inputValue.getBytes("UTF-8"));
                        }
                        catch (FileNotFoundException e1)
                        {
                            e1.printStackTrace();
                        }
                        catch (UnsupportedEncodingException e1)
                        {
                            e1.printStackTrace();
                        }
                        catch (IOException e1)
                        {
                            e1.printStackTrace();
                        }
                    }
                    else
                    {
                        //输入不标准
                        JOptionPane.showMessageDialog(null,"输入不标准");
                    }
                }
            }
        });
        onlineItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                VideoInput videoInput=new VideoInput(onlineAddress,true);
                for(WaveChart waveChart:map.values())
                {
                    waveChart.clear();
                }
                videoInput.accept(map);
            }
        });
    }
    @Override
    public void accept(Map<String,WaveChart> map) throws NullPointerException
    {
        if(map==null)
        {
            System.out.println("图表错误");
        }
        this.map=map;
    }
    public void setImage(String name)
    {
        File file=new File(name);
        if(file.exists())
        {
            //如果文件存在
            label.setIcon(new ImageIcon(name));
        }
    }
}
