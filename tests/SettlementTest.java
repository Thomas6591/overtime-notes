import cn.lan.overtime.Settlement;
import java.io.*;
public class SettlementTest {
 public static void main(String[] args)throws Exception{
  int count=0;try(BufferedReader in=new BufferedReader(new FileReader(args[0]))){String line;while((line=in.readLine())!=null){String[] a=line.split("\\t");int[] actual=Settlement.allocation(Long.parseLong(a[0]),Long.parseLong(a[1]),Integer.parseInt(a[2]),Integer.parseInt(a[3]),"1".equals(a[4]));for(int i=0;i<actual.length;i++)if(actual[i]!=Integer.parseInt(a[i+5]))throw new AssertionError("Vector "+count+" field "+i);count++;}}
  String[] b=Settlement.bounds("2024-03",31);if(!b[0].equals("2024-02-29")||!b[1].equals("2024-03-30"))throw new AssertionError("Leap year");
  System.out.println("PASS: "+count+" allocation vectors agree between Java and JavaScript");
 }
}
