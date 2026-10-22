import cn.lan.overtime.TimeMath;
public class TimeMathTest {
 static int count=0;
 static void check(boolean b){count++;if(!b)throw new AssertionError("case "+count);}
 static void invalid(Runnable r){count++;try{r.run();throw new AssertionError("Expected invalid case "+count);}catch(IllegalArgumentException expected){}}
 public static void main(String[] args){
  long start=TimeMath.start("2026-10-21","23:00"),end=TimeMath.end("2026-10-21","02:00",true);check(TimeMath.minutes(start,end,20)==160);
  invalid(()->TimeMath.start("2026-02-29","18:00"));invalid(()->TimeMath.start("2026-04-31","18:00"));invalid(()->TimeMath.start("2026-10-21","24:00"));invalid(()->TimeMath.minutes(start,end,-1));invalid(()->TimeMath.minutes(start,end,180));invalid(()->TimeMath.minutes(end,start,0));invalid(()->TimeMath.minutes(start,end+86400000L,0));check(TimeMath.minutes(start,start+86400000L,0)==1440);check(!TimeMath.overlap(1,2,2,3));check(TimeMath.overlap(1,3,2,4));check(TimeMath.start("2024-02-29","12:00")>0);System.out.println("PASS: "+count+" native calculation assertions");
 }
}
