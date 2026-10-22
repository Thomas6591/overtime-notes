package cn.lan.overtime;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.view.View;
import android.webkit.*;
import android.net.Uri;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
 private WebView web;
 private SQLiteDatabase db;
 private LocaleText localeText;
 private volatile String language;
 private String pendingExport;
 private static final int EXPORT=41,IMPORT=42;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  getWindow().setStatusBarColor(Color.rgb(246,247,242));getWindow().setNavigationBarColor(Color.rgb(246,247,242));
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  FrameLayout root=new FrameLayout(this);
  root.setBackgroundColor(Color.rgb(246,247,242));
  root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
  web=new WebView(this);root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
  db=SQLiteDatabase.openOrCreateDatabase(new File(getNoBackupFilesDir(),"overtime.db"),null);
  db.execSQL("CREATE TABLE IF NOT EXISTS records (id TEXT PRIMARY KEY, payload TEXT NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
  try{localeText=new LocaleText(getAssets().open("locales.json"));}catch(Exception e){throw new IllegalStateException("Missing locale dictionary",e);}
  language=LocaleText.defaultLanguage(Locale.getDefault());
  try(Cursor c=db.rawQuery("SELECT value FROM settings WHERE key='language'",null)){if(c.moveToFirst()&&Arrays.asList("zh","en").contains(c.getString(0)))language=c.getString(0);}

  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setBlockNetworkLoads(true);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  WebView.setWebContentsDebuggingEnabled(false);
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r){if(!r.getUrl().toString().startsWith("file:///android_asset/"))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));return null;}
  });
  web.setWebChromeClient(new WebChromeClient());web.addJavascriptInterface(new Bridge(),"LocalApp");web.loadUrl("file:///android_asset/index.html");
 }
 private JSONObject response(boolean ok,String message)throws JSONException{JSONObject out=new JSONObject();out.put("ok",ok);out.put("message",localeText.message(language,message));return out;}
 private String fail(Exception e){try{return response(false,e instanceof IllegalArgumentException?e.getMessage():e instanceof JSONException?"数据格式不正确，请检查输入或备份文件":"操作失败，原有数据已保留，请重试").toString();}catch(Exception ignored){return "{\"ok\":false,\"message\":\"Operation failed\"}";}}
 private synchronized JSONArray records()throws JSONException{
  JSONArray a=new JSONArray();try(Cursor c=db.rawQuery("SELECT payload FROM records ORDER BY start_ms DESC",null)){while(c.moveToNext())a.put(new JSONObject(c.getString(0)));}return a;
 }
 private JSONObject normalized(JSONObject r)throws Exception{
  String date=r.getString("date"),start=r.getString("start"),end=r.getString("end"),type=r.getString("type"),note=r.getString("note");
  if(!(r.get("nextDay") instanceof Boolean))throw new IllegalArgumentException("次日标记格式不正确");
  boolean next=r.getBoolean("nextDay");Object br=r.get("break");if(!(br instanceof Number)||((Number)br).doubleValue()!=((Number)br).intValue())throw new IllegalArgumentException("休息时长须为整数分钟");int rest=((Number)br).intValue();
  TimeMath.minutes(TimeMath.start(date,start),TimeMath.end(date,end,next),rest);
  if(!Arrays.asList("工作日","休息日","节假日").contains(type))throw new IllegalArgumentException("加班类型不正确");if(note.length()>500)throw new IllegalArgumentException("备注最多500个字");
  String id=r.optString("id","");if(id.isEmpty())id=UUID.randomUUID().toString();if(!id.matches("[a-zA-Z0-9-]{1,64}"))throw new IllegalArgumentException("记录编号不正确");
  JSONObject out=new JSONObject();out.put("id",id);out.put("date",date);out.put("start",start);out.put("end",end);out.put("nextDay",next);out.put("break",rest);out.put("type",type);out.put("note",note);return out;
 }
 private long startOf(JSONObject r)throws Exception{return TimeMath.start(r.getString("date"),r.getString("start"));}
 private long endOf(JSONObject r)throws Exception{return TimeMath.end(r.getString("date"),r.getString("end"),r.getBoolean("nextDay"));}
 private boolean overlaps(JSONObject r)throws Exception{try(Cursor c=db.rawQuery("SELECT id FROM records WHERE start_ms < ? AND end_ms > ? AND id != ? LIMIT 1",new String[]{""+endOf(r),""+startOf(r),r.getString("id")})){return c.moveToFirst();}}
 private void write(JSONObject r)throws Exception{ContentValues v=new ContentValues();v.put("id",r.getString("id"));v.put("payload",r.toString());v.put("start_ms",startOf(r));v.put("end_ms",endOf(r));if(db.insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IOException("write failed");}
 private boolean contains(String id){try(Cursor c=db.rawQuery("SELECT id FROM records WHERE id=?",new String[]{id})){return c.moveToFirst();}}
 private void notifyPage(boolean ok,String message){runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.nativeEvent("+ok+","+JSONObject.quote(localeText.message(language,message))+")",null);});}
 public class Bridge {
  @JavascriptInterface public String getLanguage(){try{JSONObject o=response(true,"");o.put("language",language);return o.toString();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public String setLanguage(String next){synchronized(MainActivity.this){try{
   if(!Arrays.asList("zh","en").contains(next))throw new IllegalArgumentException("语言选择保存失败，请重试");
   ContentValues v=new ContentValues();v.put("key","language");v.put("value",next);
   if(db.insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IOException("Language write failed");
   language=next;JSONObject o=response(true,"");o.put("language",language);return o.toString();
  }catch(Exception e){return fail(e);}}}

  @JavascriptInterface public String list(){synchronized(MainActivity.this){try{JSONObject o=response(true,"");o.put("records",records());return o.toString();}catch(Exception e){return fail(e);}}}
  @JavascriptInterface public String save(String raw){synchronized(MainActivity.this){try{JSONObject input=new JSONObject(raw);String oldId=input.optString("id","");if(!oldId.isEmpty()&&!contains(oldId))throw new IllegalArgumentException("记录已不存在，请返回后重试");JSONObject r=normalized(input);if(overlaps(r))throw new IllegalArgumentException("这段时间与已有记录重叠，请检查日期和时间");write(r);return response(true,"已保存在本机").toString();}catch(Exception e){return fail(e);}}}
  @JavascriptInterface public String delete(String id){synchronized(MainActivity.this){try{db.delete("records","id=?",new String[]{id});return response(true,"记录已删除").toString();}catch(Exception e){return fail(e);}}}
  @JavascriptInterface public void exportFile(String kind){runOnUiThread(()->{if(pendingExport!=null){notifyPage(false,"正在等待文件保存，请先完成当前操作");return;}try{JSONArray a; synchronized(MainActivity.this){a=records();}String ext,mime;if("csv".equals(kind)){StringBuilder b=new StringBuilder("\uFEFF"+localeText.text(language,"csv.header")+"\r\n");for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);int minutes=TimeMath.minutes(startOf(r),endOf(r),r.getInt("break"));String[] cols={r.getString("date"),r.getString("start"),r.getString("end"),localeText.text(language,r.getBoolean("nextDay")?"common.yes":"common.no"),""+r.getInt("break"),""+minutes,String.format(Locale.ROOT,"%.2f",minutes/60.0),localeText.message(language,r.getString("type")),r.getString("note")};for(int j=0;j<cols.length;j++){if(j>0)b.append(',');String value=cols[j];if(!value.trim().isEmpty()&&"=+@-".indexOf(value.trim().charAt(0))>=0)value="'"+value;b.append('"').append(value.replace("\"","\"\"")).append('"');}b.append("\r\n");}pendingExport=b.toString();ext="csv";mime="text/csv";}else{JSONObject b=new JSONObject();b.put("app","cn.lan.overtime");b.put("version",1);b.put("exportedAt",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.ROOT).format(new Date()));b.put("records",a);pendingExport=b.toString(2);ext="json";mime="application/json";}
    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(mime);intent.putExtra(Intent.EXTRA_LOCAL_ONLY,true);intent.putExtra(Intent.EXTRA_TITLE,localeText.text(language,"app.filename")+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date())+"."+ext);startActivityForResult(intent,EXPORT);
   }catch(Exception e){pendingExport=null;notifyPage(false,"无法打开文件保存窗口，请检查手机文件管理器");}});}
  @JavascriptInterface public void importFile(){runOnUiThread(()->{try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_LOCAL_ONLY,true);startActivityForResult(i,IMPORT);}catch(Exception e){notifyPage(false,"无法打开文件选择窗口");}});}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null){if(request==EXPORT)pendingExport=null;notifyPage(false,"已取消");return;}Uri uri=data.getData();if(request==EXPORT){String text=pendingExport;pendingExport=null;if(text==null){notifyPage(false,"保存中断，请重新导出");return;}new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();out.write(text.getBytes(StandardCharsets.UTF_8));notifyPage(true,"文件已保存，请妥善保管");}catch(Exception e){notifyPage(false,"文件保存失败，请重试");}}).start();}
  else if(request==IMPORT){new Thread(()->{try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(bytes.size()+n>8*1024*1024)throw new IllegalArgumentException("备份文件过大，上限8MB");bytes.write(buf,0,n);}}JSONObject backup=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));if(!"cn.lan.overtime".equals(backup.getString("app"))||backup.getInt("version")!=1)throw new IllegalArgumentException("请选择加班手记导出的原始备份文件");JSONArray list=backup.getJSONArray("records");if(list.length()>10000)throw new IllegalArgumentException("单个备份最多10000条记录");ArrayList<JSONObject> checked=new ArrayList<>();for(int j=0;j<list.length();j++){JSONObject item=list.getJSONObject(j);if(item.optString("id","").isEmpty())throw new IllegalArgumentException("备份缺少记录编号");checked.add(normalized(item));}int added=0,skipped=0,conflicts=0;synchronized(MainActivity.this){db.beginTransaction();try{for(JSONObject r:checked){if(contains(r.getString("id"))){skipped++;continue;}if(overlaps(r)){conflicts++;continue;}write(r);added++;}db.setTransactionSuccessful();}finally{db.endTransaction();}}notifyPage(true,localeText.text(language,"import.result",added,skipped,conflicts));}catch(Exception e){notifyPage(false,e instanceof IllegalArgumentException?localeText.text(language,"import.failed",localeText.message(language,e.getMessage())):e instanceof JSONException?localeText.text(language,"import.failed",localeText.text(language,"error.format")):localeText.text(language,"error.import"));}}).start();}}
 @Override public void onBackPressed(){web.evaluateJavascript("window.appBack()",v->{if("false".equals(v))super.onBackPressed();});}
 @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("LocalApp");web.destroy();web=null;}/* SQLite stays open until process exit, avoiding races with a pending import. */super.onDestroy();}
}
