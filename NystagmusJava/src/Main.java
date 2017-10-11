import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * Created by ZingBug on 2017/10/11.
 */
public class Main {
    private static final String name="F:\\GitHub\\NystagmusJava\\NystagmusJava\\1.jpg";
    public static void main(String[] args)
    {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                ImageViewerFrame frame=new ImageViewerFrame();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
                //frame.setImage(name);

            }
        });
    }
}
class ImageViewerFrame extends JFrame{
    private JLabel label;
    private JFileChooser chooser;
    private static final int DEFAULT_WIDTH=300;
    private static final int DEFAULT_HEIGHT=400;

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
        openItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int result=chooser.showOpenDialog(null);
                if(result==JFileChooser.APPROVE_OPTION)
                {
                    /*
                    String name=chooser.getSelectedFile().getPath();
                    //F:\GitHub\NystagmusJava\NystagmusJava\1.jpg
                    label.setIcon(new ImageIcon(name));
                    pack();//自适应大小
                    */
                    /*读取到的视频路径*/
                    String VideoPath=chooser.getSelectedFile().getPath();
                    VideoInput videoInput=new VideoInput(VideoPath);
                }
            }
        });
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
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
