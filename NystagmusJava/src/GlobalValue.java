/**
 * Created by ZingBug on 2018/11/17.
 */
public class GlobalValue {

    public static final int HighTidePeriodSecond=3;//最大眼震反应期时间,单位为s
    public static final double SPVMaxValue=0.5f;//SPV最大临界值，超过这个值即眼震眩晕异常


    public volatile static boolean isSaveXdata=true;

    public volatile static String saveNumber="";

    public static String saveDataPath="./data";

    public static int saveStartFrameNumber=3000;

    public static int saveFrameNumber=1500;

    private GlobalValue(){}
}
