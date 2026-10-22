import cn.lan.overtime.LocaleText;
import java.io.FileInputStream;
import java.util.Locale;
public class LocaleTextTest {
 public static void main(String[] args)throws Exception{
  LocaleText text=new LocaleText(new FileInputStream(args[0]));
  check(LocaleText.defaultLanguage(Locale.CHINA).equals("zh"));check(LocaleText.defaultLanguage(Locale.TAIWAN).equals("zh"));check(LocaleText.defaultLanguage(Locale.US).equals("en"));check(LocaleText.defaultLanguage(Locale.FRANCE).equals("en"));
  check(text.message("en","工作日").equals("Workday"));check(text.message("en","休息日").equals("Rest day"));check(text.message("en","节假日").equals("Holiday"));check(text.message("zh","工作日").equals("工作日"));
  check(text.message("en","已保存在本机").equals("Saved on this device."));check(text.message("en","休息时长必须小于本次总时长").startsWith("Break time"));check(text.message("en","请选择2000—2100年的有效日期和时间").startsWith("Choose a valid"));
  check(text.text("en","csv.header").startsWith("Date,Start time,End time"));check(text.text("en","common.yes").equals("Yes"));check(text.text("zh","common.yes").equals("是"));
  check(text.text("en","import.result",2,3,4).equals("Entries imported: 2. Existing IDs skipped: 3; overlapping entries skipped: 4. Existing records were kept."));check(text.text("zh","import.result",2,3,4).contains("已导入2条"));
  check(text.message("en",null).contains("operation failed"));System.out.println("PASS: 17 native locale, legacy type, export header and import-message assertions");
 }
 static void check(boolean value){if(!value)throw new AssertionError();}
}
