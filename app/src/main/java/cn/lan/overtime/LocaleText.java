package cn.lan.overtime;

import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Reads the same offline dictionary used by the embedded interface. */
public final class LocaleText {
 private final JSONObject data;
 public LocaleText(InputStream input) throws Exception {
  try(InputStream in=input;ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
   byte[] buffer=new byte[8192];int n;
   while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
   data=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
  }
 }
 public static String defaultLanguage(Locale locale){return "zh".equals(locale.getLanguage())?"zh":"en";}
 public String text(String language,String key,Object... arguments){
  try{
   String result=data.getJSONObject(language).optString(key,data.getJSONObject("en").optString(key,key));
   for(int i=0;i<arguments.length;i++)result=result.replace("{"+i+"}",String.valueOf(arguments[i]));
   return result;
  }catch(Exception e){return key;}
 }
 public String message(String language,String original){
  if(original==null)return text(language,"error.generic");
  String key=data.optJSONObject("messages").optString(original,"");
  return key.isEmpty()?text(language,original):text(language,key);
 }
}
