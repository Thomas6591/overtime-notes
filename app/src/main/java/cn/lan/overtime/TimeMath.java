package cn.lan.overtime;
import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import java.util.Locale;
import java.util.TimeZone;
public final class TimeMath {
 public static long start(String date,String time) {
  if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || !time.matches("[0-9]{2}:[0-9]{2}")) throw new IllegalArgumentException("日期或时间格式不正确");
  SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT);fmt.setLenient(false);fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
  String s=date+" "+time;ParsePosition pos=new ParsePosition(0);java.util.Date d=fmt.parse(s,pos);
  if(d==null||pos.getIndex()!=s.length()||Integer.parseInt(date.substring(0,4))<2000||Integer.parseInt(date.substring(0,4))>2100)throw new IllegalArgumentException("请选择2000—2100年的有效日期和时间");
  return d.getTime();
 }
 public static long end(String date,String time,boolean next){return start(date,time)+(next?86400000L:0);}
 public static int minutes(long start,long end,int rest){long gross=(end-start)/60000;if(gross<=0||gross>1440)throw new IllegalArgumentException("结束时间需晚于开始时间，单条记录不能超过24小时");if(rest<0||rest>=gross)throw new IllegalArgumentException("休息时长必须小于本次总时长");return (int)gross-rest;}
 public static boolean overlap(long a,long b,long c,long d){return a<d&&c<b;}
}
