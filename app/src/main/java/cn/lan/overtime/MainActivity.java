package cn.lan.overtime;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.res.Configuration;
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
 private FrameLayout root;
 private OvertimeStore store;
 private LocaleText localeText;
 private volatile String language;
 private String pendingExport;
 private boolean pendingBackup,importBusy;
 private JSONObject pendingImport;
 private static final int EXPORT=41,IMPORT=42;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  root=new FrameLayout(this);
  root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
  web=new WebView(this);root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
  store=new OvertimeStore(SQLiteDatabase.openOrCreateDatabase(new File(getNoBackupFilesDir(),"overtime.db"),null));
  try{localeText=new LocaleText(getAssets().open("locales.json"));}catch(Exception e){throw new IllegalStateException("Missing locale dictionary",e);}
  language=store.getSetting("language",LocaleText.defaultLanguage(Locale.getDefault()));
  try{applyChrome(store.preferences().optString("theme","system"));}catch(Exception e){applyChrome("system");}
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setBlockNetworkLoads(true);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setTextZoom(Math.round(100*getResources().getConfiguration().fontScale));
  WebView.setWebContentsDebuggingEnabled(false);
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r){if(!r.getUrl().toString().startsWith("file:///android_asset/"))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));return null;}
  });
  web.setWebChromeClient(new WebChromeClient());web.addJavascriptInterface(new Bridge(),"LocalApp");web.loadUrl("file:///android_asset/index.html");
 }
 private void applyChrome(String theme){boolean dark="dark".equals(theme)||("system".equals(theme)&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);int color=Color.parseColor(dark?"#121e1b":"soft".equals(theme)?"#edf2e8":"#f6f7f2");root.setBackgroundColor(color);web.setBackgroundColor(color);getWindow().setStatusBarColor(color);getWindow().setNavigationBarColor(color);getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
 @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(web!=null)web.getSettings().setTextZoom(Math.round(100*config.fontScale));}
 private JSONObject response(boolean ok,String message)throws JSONException{JSONObject out=new JSONObject();out.put("ok",ok);out.put("message",localeText.message(language,message));return out;}
 private String fail(Exception e){try{return response(false,e instanceof IllegalArgumentException?e.getMessage():e instanceof JSONException?"数据格式不正确，请检查输入或备份文件":"操作失败，原有数据已保留，请重试").toString();}catch(Exception ignored){return "{\"ok\":false,\"message\":\"Operation failed\"}";}}
 private String text(String key,Object... args){return localeText.text(language,key,args);}
 private void evaluate(String js){runOnUiThread(()->{if(web!=null)web.evaluateJavascript(js,null);});}
 private void notifyPage(boolean ok,String message){evaluate("window.nativeEvent("+ok+","+JSONObject.quote(localeText.message(language,message))+")");}
 public class Bridge {
  @JavascriptInterface public String getLanguage(){try{JSONObject o=response(true,"");o.put("language",language);return o.toString();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public String setLanguage(String next){try{if(!Arrays.asList("zh","en").contains(next))throw new IllegalArgumentException("语言选择保存失败，请重试");store.setSetting("language",next);language=next;return getLanguage();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public String list(){try{JSONObject o=response(true,"");o.put("records",store.records());return o.toString();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public String getState(){try{JSONObject o=store.state();o.put("ok",true);return o.toString();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public String save(String raw){try{JSONObject o=response(true,"已保存在本机");o.put("record",store.save(new JSONObject(raw)));return o.toString();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public String saveBatch(String raw){try{JSONObject o=response(true,"");JSONArray results=store.batch(new JSONArray(raw));for(int i=0;i<results.length();i++){JSONObject r=results.getJSONObject(i);if(r.has("message"))r.put("message",localeText.message(language,r.getString("message")));}o.put("results",results);return o.toString();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public String delete(String id){try{store.delete(id);return response(true,"trash.moved").toString();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public String restoreRecord(String id){try{store.restore(id);return response(true,"trash.restored").toString();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public String removeTrash(String id){try{store.removeTrash(id);return response(true,"trash.removed").toString();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public String saveTemplate(String raw){try{store.saveTemplate(new JSONObject(raw));return response(true,"template.saved").toString();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public String deleteTemplate(String id){try{store.deleteTemplate(id);return response(true,"template.deleted").toString();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public String setPreferences(String raw){try{JSONObject p=new JSONObject(raw);store.preferences(p);runOnUiThread(()->applyChrome(p.optString("theme","system")));return response(true,"appearance.saved").toString();}catch(Exception e){return fail(e);}}
  @JavascriptInterface public void exportFile(String kind){runOnUiThread(()->{
   if(pendingExport!=null){notifyPage(false,"正在等待文件保存，请先完成当前操作");return;}
   try{String ext,mime;if("csv".equals(kind)){JSONArray a=store.records();StringBuilder b=new StringBuilder("\uFEFF"+text("csv.header")+"\r\n");for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);int minutes=TimeMath.minutes(OvertimeStore.start(r),OvertimeStore.end(r),r.getInt("break"));String[] cols={r.getString("date"),r.getString("start"),r.getString("end"),text(r.getBoolean("nextDay")?"common.yes":"common.no"),""+r.getInt("break"),""+minutes,String.format(Locale.ROOT,"%.2f",minutes/60.0),localeText.message(language,r.getString("type")),r.getString("note")};for(int j=0;j<cols.length;j++){if(j>0)b.append(',');String value=cols[j];if(!value.trim().isEmpty()&&"=+@-".indexOf(value.trim().charAt(0))>=0)value="'"+value;b.append('"').append(value.replace("\"","\"\"")).append('"');}b.append("\r\n");}pendingExport=b.toString();ext="csv";mime="text/csv";pendingBackup=false;}else if("json".equals(kind)){JSONObject b=store.backup();b.put("exportedAt",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.ROOT).format(new Date()));pendingExport=b.toString(2);ext="json";mime="application/json";pendingBackup=true;}else throw new IllegalArgumentException();
    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(mime);intent.putExtra(Intent.EXTRA_LOCAL_ONLY,true);intent.putExtra(Intent.EXTRA_TITLE,text("app.filename")+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date())+"."+ext);startActivityForResult(intent,EXPORT);
   }catch(Exception e){pendingExport=null;notifyPage(false,"无法打开文件保存窗口，请检查手机文件管理器");}
  });}
  @JavascriptInterface public void importFile(){synchronized(MainActivity.this){if(importBusy||pendingImport!=null){notifyPage(false,"import.busy");return;}importBusy=true;}runOnUiThread(()->{try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_LOCAL_ONLY,true);startActivityForResult(i,IMPORT);}catch(Exception e){synchronized(MainActivity.this){importBusy=false;}notifyPage(false,"无法打开文件选择窗口");}});}
  @JavascriptInterface public String confirmImport(boolean restoreSettings){synchronized(MainActivity.this){try{if(pendingImport==null)throw new IllegalArgumentException("import.noPreview");JSONObject o=store.importBackup(pendingImport,restoreSettings);pendingImport=null;language=store.getSetting("language",language);runOnUiThread(()->{try{applyChrome(store.preferences().optString("theme","system"));}catch(Exception ignored){}});o.put("ok",true);return o.toString();}catch(Exception e){pendingImport=null;return fail(e);}}}
  @JavascriptInterface public void cancelImport(){synchronized(MainActivity.this){pendingImport=null;}}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
  if(result!=RESULT_OK||data==null||data.getData()==null){if(request==EXPORT)pendingExport=null;if(request==IMPORT)synchronized(this){importBusy=false;}notifyPage(false,"已取消");return;}Uri uri=data.getData();
  if(request==EXPORT){String content=pendingExport;boolean backup=pendingBackup;pendingExport=null;if(content==null){notifyPage(false,"保存中断，请重新导出");return;}new Thread(()->{try{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();out.write(content.getBytes(StandardCharsets.UTF_8));out.flush();}if(backup)store.setSetting("lastBackup",new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).format(new Date()));notifyPage(true,"文件已保存，请妥善保管");}catch(Exception e){notifyPage(false,"文件保存失败，请重试");}}).start();}
  else if(request==IMPORT){new Thread(()->{try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(bytes.size()+n>64*1024*1024)throw new IllegalArgumentException("备份文件过大，上限64MB");bytes.write(buf,0,n);}}JSONObject checked=store.validateBackup(new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)));JSONObject preview=store.preview(checked);synchronized(MainActivity.this){pendingImport=checked;importBusy=false;}evaluate("window.nativeImportPreview("+preview.toString()+")");}catch(Exception e){synchronized(MainActivity.this){pendingImport=null;importBusy=false;}notifyPage(false,e instanceof IllegalArgumentException?text("import.failed",localeText.message(language,e.getMessage())):e instanceof JSONException?text("import.failed",text("error.format")):text("error.import"));}}).start();}
 }
 @Override public void onBackPressed(){web.evaluateJavascript("window.appBack()",v->{if("false".equals(v))super.onBackPressed();});}
 @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("LocalApp");web.destroy();web=null;}super.onDestroy();}
}
