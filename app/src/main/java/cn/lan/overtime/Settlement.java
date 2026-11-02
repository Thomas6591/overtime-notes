package cn.lan.overtime;

import java.time.LocalDate;
import java.time.YearMonth;

/** Wall-clock minutes, independent of the device time zone and daylight saving. */
public final class Settlement {
 public static String[] bounds(String month,int day){
  if(day<1||day>31)throw new IllegalArgumentException("v13.badRules");
  YearMonth m=YearMonth.parse(month);
  LocalDate endExclusive=day==1?m.plusMonths(1).atDay(1):m.atDay(Math.min(day,m.lengthOfMonth()));
  YearMonth before=m.minusMonths(1);
  LocalDate start=day==1?m.atDay(1):before.atDay(Math.min(day,before.lengthOfMonth()));
  if(start.isBefore(LocalDate.of(2000,1,1)))start=LocalDate.of(2000,1,1);
  return new String[]{start.toString(),endExclusive.minusDays(1).toString()};
 }
 public static int[] allocation(long start,long end,int rest,int step,boolean split){
  int raw=TimeMath.minutes(start,end,rest);
  if(step!=1&&step!=5&&step!=15&&step!=30)throw new IllegalArgumentException("v13.badRules");
  int gross=(int)((end-start)/60000),effective=raw/step*step;
  long midnight=(Math.floorDiv(start,86400000L)+1)*86400000L;
  int firstGross=split?(int)((Math.min(end,midnight)-start)/60000):gross;
  int firstRest=(int)((long)rest*firstGross/gross);
  int firstRaw=firstGross-firstRest;
  int firstEffective=(int)((long)effective*firstRaw/raw);
  return new int[]{firstGross,gross-firstGross,firstRest,rest-firstRest,firstRaw,raw-firstRaw,firstEffective,effective-firstEffective,raw,effective};
 }
}
