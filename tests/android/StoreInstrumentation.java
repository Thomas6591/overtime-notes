package cn.lan.overtime;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import android.os.Bundle;
import org.json.*;
import java.io.File;

/** Runs against actual Android SQLite, not a browser bridge simulation. */
public final class StoreInstrumentation extends Instrumentation {
 private String mode;
 private int count;
 @Override public void onCreate(Bundle args){super.onCreate(args);mode=args.getString("mode","tests");start();}
 private void check(boolean value,String label){count++;if(!value)throw new AssertionError(label);}
 private JSONObject r(String id,String date,String start,String end)throws Exception{return new JSONObject().put("id",id).put("date",date).put("start",start).put("end",end).put("nextDay",false).put("break",0).put("type","工作日").put("note","Native test");}
 private void seed()throws Exception{
  SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(new File(getTargetContext().getNoBackupFilesDir(),"overtime.db"),null);
  db.execSQL("CREATE TABLE IF NOT EXISTS records (id TEXT PRIMARY KEY,payload TEXT NOT NULL,start_ms INTEGER NOT NULL,end_ms INTEGER NOT NULL)");db.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY,value TEXT NOT NULL)");
  JSONObject r=r("upgrade-fixture","2026-10-24","18:00","20:30");ContentValues v=new ContentValues();v.put("id","upgrade-fixture");v.put("payload",r.toString());v.put("start_ms",1792864800000L);v.put("end_ms",1792873800000L);db.insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE);v=new ContentValues();v.put("key","language");v.put("value","en");db.insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);db.close();
 }
 private void tests()throws Exception{
  Context context=getTargetContext();SQLiteDatabase actual=SQLiteDatabase.openOrCreateDatabase(new File(context.getNoBackupFilesDir(),"overtime.db"),null);OvertimeStore upgrade=new OvertimeStore(actual);
  check(upgrade.records().getJSONObject(0).getString("id").equals("upgrade-fixture"),"v1 record survives install -r");check(upgrade.getSetting("language","").equals("en"),"language survives install -r");check(upgrade.records().getJSONObject(0).getString("note").equals("Native test"),"legacy payload unchanged");actual.close();
  File file=new File(context.getCacheDir(),"store-verification.db");SQLiteDatabase.deleteDatabase(file);SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(file,null);OvertimeStore s=new OvertimeStore(db);
  JSONObject a=s.save(r("","2026-10-01","18:00","20:00"));String id=a.getString("id");check(s.records().length()==1,"native save");check(s.preferences().getBoolean("animation"),"default preferences");
  try{s.save(r("","2026-10-01","19:00","21:00"));throw new AssertionError("expected overlap");}catch(IllegalArgumentException expected){count++;}
  JSONObject night=r("","2026-10-02","23:00","02:00").put("nextDay",true).put("break",30);s.save(night);check(s.records().length()==2,"overnight save");try{s.save(r("","2026-10-03","01:00","03:00"));throw new AssertionError("overnight conflict");}catch(IllegalArgumentException expected){count++;}
  s.delete(id);check(s.records().length()==1&&s.trash().length()==1,"trash transaction");s.restore(id);check(s.records().length()==2&&s.trash().length()==0,"restore transaction");s.delete(id);JSONObject replacement=s.save(r("","2026-10-01","18:00","20:00"));try{s.restore(id);throw new AssertionError("restore conflict");}catch(IllegalArgumentException expected){count++;}check(s.trash().length()==1,"failed restore retains trash");s.delete(replacement.getString("id"));s.restore(id);check(s.records().length()==2,"restore after conflict removal");s.removeTrash("*");check(s.trash().length()==0,"empty trash");
  JSONArray result=s.batch(new JSONArray().put(r("","2026-10-01","19:00","21:00")).put(r("","2026-10-04","18:00","20:00")));check(!result.getJSONObject(0).getBoolean("ok")&&result.getJSONObject(1).getBoolean("ok"),"batch partial result");check(s.records().length()==3,"batch writes only valid date");
  s.saveTemplate(new JSONObject().put("name","Evening").put("record",a));check(s.templates().length()==1,"template save");String tid=s.templates().getJSONObject(0).getString("id");s.saveTemplate(s.templates().getJSONObject(0).put("name","Renamed"));check(s.templates().length()==1&&s.templates().getJSONObject(0).getString("name").equals("Renamed"),"template rename");
  JSONObject backup=s.backup(),checked=s.validateBackup(backup),p=s.preview(checked);check(p.getInt("added")==0&&p.getInt("duplicates")==3,"native preview duplicates");check(s.records().length()==3,"preview does not write");
  JSONObject legacy=new JSONObject().put("app","cn.lan.overtime").put("version",1).put("records",new JSONArray().put(r("legacy-new","2026-10-05","18:00","20:00")).put(r("legacy-conflict","2026-10-04","19:00","21:00")));
  checked=s.validateBackup(legacy);check(s.preview(checked).getInt("added")==1&&s.preview(checked).getInt("conflicts")==1,"legacy import preview");check(s.records().length()==3,"legacy preview no write");JSONObject imported=s.importBackup(checked,false);check(imported.getInt("added")==1&&imported.getInt("conflicts")==1,"legacy import result");
  JSONObject invalid=new JSONObject(legacy.toString());invalid.getJSONArray("records").getJSONObject(1).put("date","2026-02-30");int before=s.records().length();try{s.validateBackup(invalid);throw new AssertionError("invalid backup accepted");}catch(IllegalArgumentException expected){count++;}check(s.records().length()==before,"invalid backup preserves records");
  JSONObject race=new JSONObject().put("app","cn.lan.overtime").put("version",1).put("records",new JSONArray().put(r("race","2026-10-06","18:00","20:00")));checked=s.validateBackup(race);check(s.preview(checked).getInt("added")==1,"race preview");s.save(r("","2026-10-06","19:00","21:00"));check(s.importBackup(checked,false).getInt("conflicts")==1,"recheck conflicts on commit");
  s.preferences(new JSONObject().put("theme","dark").put("font","large").put("animation",false));s.setSetting("language","en");JSONObject v2=s.backup();s.preferences(new JSONObject().put("theme","light").put("font","system").put("animation",true));s.importBackup(s.validateBackup(v2),false);check(s.preferences().getString("theme").equals("light"),"default keeps display settings");s.importBackup(s.validateBackup(v2),true);check(s.preferences().getString("theme").equals("dark")&&!s.preferences().getBoolean("animation"),"optional settings recovery");
  s.deleteTemplate(tid);check(s.templates().length()==0,"template delete");
  s.delete(id);db.execSQL("UPDATE trash SET deleted_at=1 WHERE id=?",new Object[]{id});check(s.trash().length()==0,"expired trash purged");
  String snapshot=s.records().toString();db.close();db=SQLiteDatabase.openOrCreateDatabase(file,null);s=new OvertimeStore(db);check(s.records().toString().equals(snapshot),"SQLite survives close and reopen");check(s.preferences().getString("theme").equals("dark"),"appearance persists in SQLite");db.close();SQLiteDatabase.deleteDatabase(file);
 }

 private interface Operation {void run()throws Exception;}
 private void rejected(Operation f,String label)throws Exception{try{f.run();throw new AssertionError(label);}catch(IllegalArgumentException expected){count++;}}
 private void v13()throws Exception{
  File f=new File(getTargetContext().getCacheDir(),"settlement-tests.db");SQLiteDatabase.deleteDatabase(f);SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(f,null);OvertimeStore s=new OvertimeStore(db);
  s.prepareUpgrade();check(s.recoveries().length()==1,"upgrade protects data once");s.prepareUpgrade();check(s.recoveries().length()==1,"upgrade snapshot idempotent");
  JSONObject night=s.save(r("","2026-10-31","22:00","02:00").put("nextDay",true).put("break",30));String id=night.getString("id");check(s.checkPeriod("2026-10").getInt("total")==210,"legacy allocation default");
  JSONObject pref=s.preferences().put("split",true).put("rounding",15);s.preferences(pref);check(s.checkPeriod("2026-10").getInt("total")==105,"midnight first part");check(s.checkPeriod("2026-11").getInt("total")==105,"midnight second part");check(s.checkPeriod("2026-11").getInt("count")==1,"second-day original record count");
  s.lockPeriod("2026-11",true);check(s.locks().length()==1,"lock stored");rejected(()->s.save(new JSONObject(night.toString()).put("date","2026-10-30")),"moving locked original rejected");rejected(()->s.delete(id),"delete locked entry rejected");rejected(()->s.save(r("","2026-11-03","18:00","19:00")),"adding to locked period rejected");rejected(()->s.preferences(new JSONObject(pref.toString()).put("cycleDay",26)),"rule change with lock rejected");s.preferences(new JSONObject(pref.toString()).put("theme","dark"));check(s.preferences().getString("theme").equals("dark"),"appearance permitted with lock");
  JSONObject backup=s.validateBackup(new JSONObject().put("app","cn.lan.overtime").put("version",1).put("records",new JSONArray().put(r("locked-import","2026-11-10","18:00","19:00"))));check(s.preview(backup).getInt("locked")==1,"locked import preview");check(s.importBackup(backup,false).getInt("locked")==1,"locked import commit");s.lockPeriod("2026-11",false);s.preferences(s.preferences().put("cycleDay",26));JSONObject cycle=s.checkPeriod("2026-11");check(cycle.getString("from").equals("2026-10-26")&&cycle.getString("to").equals("2026-11-25")&&cycle.getInt("total")==210,"custom settlement includes both parts");
  String[] leap=Settlement.bounds("2024-03",31);check(leap[0].equals("2024-02-29")&&leap[1].equals("2024-03-30"),"short month boundary clamped");String[] year=Settlement.bounds("2027-01",26);check(year[0].equals("2026-12-26")&&year[1].equals("2027-01-25"),"year boundary");
  JSONObject changed=new JSONObject(night.toString()).put("end","03:00"),incoming=s.validateBackup(new JSONObject().put("app","cn.lan.overtime").put("version",1).put("records",new JSONArray().put(changed)));check(s.preview(incoming,"skip").getInt("duplicates")==1,"skip matching id");check(s.preview(incoming,"replace").getInt("replaced")==1,"replace preview");check(s.preview(incoming,"keep").getInt("conflicts")==1,"keep cannot bypass overlap");int before=s.records().length();check(s.importBackup(incoming,false,"replace").getInt("replaced")==1&&s.records().length()==before,"replace atomic");
  long recovery=s.recoveries().getJSONObject(0).getLong("id");s.recover(recovery);check(s.records().getJSONObject(0).getString("end").equals("02:00"),"recovery restores before import");check(s.recoveries().length()<=5,"recovery retains at most five");
  JSONObject duplicate=new JSONObject().put("app","cn.lan.overtime").put("version",1).put("records",new JSONArray().put(night).put(night));String previous=s.records().toString();rejected(()->s.importBackup(s.validateBackup(duplicate),false),"duplicate IDs reject entire import");check(previous.equals(s.records().toString()),"failed import rolls back");
  JSONObject bad=s.backup();bad.getJSONArray("records").getJSONObject(0).put("break",9999);rejected(()->s.validateBackup(bad),"bad break rejected");
  JSONObject moved=new JSONObject(night.toString()).put("date","2026-10-29");incoming=s.validateBackup(new JSONObject().put("app","cn.lan.overtime").put("version",1).put("records",new JSONArray().put(moved)));check(s.importBackup(incoming,false,"keep").getInt("added")==1&&s.records().length()==2,"keep both non-overlap with new ID");
  s.delete(id);s.lockPeriod("2026-11",true);rejected(()->s.restore(id),"trash restore respects lock");s.lockPeriod("2026-11",false);s.restore(id);
  s.setSetting("reminder",OvertimeStore.validReminder(new JSONObject().put("enabled",true).put("time","21:15").put("days",new JSONArray("[1,2,3,4,5,6,7]"))).toString());check(!ReminderReceiver.shouldNotify(s,java.time.LocalDate.parse("2026-10-31")),"today entry suppresses reminder");check(ReminderReceiver.shouldNotify(s,java.time.LocalDate.parse("2026-10-28")),"missing day eligible");ReminderReceiver.schedule(getTargetContext(),s);s.setSetting("reminder","{}");ReminderReceiver.schedule(getTargetContext(),s);check(!ReminderReceiver.shouldNotify(s,java.time.LocalDate.parse("2026-10-28")),"disabled reminder suppressed");
  check(s.validateBackup(s.backup()).getJSONArray("records").length()==2,"v3 backup validates");String original=s.records().toString();db.close();db=SQLiteDatabase.openOrCreateDatabase(f,null);OvertimeStore reopened=new OvertimeStore(db);check(reopened.records().toString().equals(original),"v13 data survives reopening");check(reopened.preferences().getInt("cycleDay")==26,"rules survive reopening");db.close();SQLiteDatabase.deleteDatabase(f);
 }
 private String js(final Activity activity,final String script)throws Exception{
  final String[] answer={null};final CountDownLatch latch=new CountDownLatch(1);
  runOnMainSync(()->{try{Field f=MainActivity.class.getDeclaredField("web");f.setAccessible(true);((WebView)f.get(activity)).evaluateJavascript(script,value->{answer[0]=value;latch.countDown();});}catch(Exception e){answer[0]="ERROR: "+e;latch.countDown();}});
  if(!latch.await(30,TimeUnit.SECONDS))throw new AssertionError("WebView JavaScript callback timed out");return answer[0];
 }
 private void webChecks()throws Exception{
  Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  Activity activity=startActivitySync(intent);
  try{
   long deadline=System.currentTimeMillis()+150000;String status="";
   while(System.currentTimeMillis()<deadline){status=js(activity,"Boolean(window.Overtime && window.Settlement && window.I18n && document.getElementById('periodRange'))");if("true".equals(status))break;Thread.sleep(1000);}
   check("true".equals(status),"real WebView loaded: "+status);
   js(activity,"document.getElementById('monthPicker').value='2026-10';document.getElementById('monthPicker').dispatchEvent(new Event('change'))");check("\"2.5\"".equals(js(activity,"document.getElementById('totalHours').textContent")),"legacy summary 2.5 hours");
   check("\"en\"".equals(js(activity,"document.documentElement.lang")),"real WebView kept English");
   js(activity,"document.querySelector('[data-scope=year]').click()");check("12".equals(js(activity,"document.querySelectorAll('.chart-hit').length")),"native year chart");
   js(activity,"document.querySelector('[data-scope=month]').click();document.getElementById('add').click();document.getElementById('date').value='2026-10-25';document.getElementById('start').value='18:00';document.getElementById('end').value='19:00';document.getElementById('note').value='WebView native check';document.getElementById('save').click()");
   check("2".equals(js(activity,"JSON.parse(LocalApp.list()).records.length")),"real form saves through native bridge");
   check("\"3.5\"".equals(js(activity,"document.getElementById('totalHours').textContent")),"real native save refreshes totals");
   js(activity,"document.getElementById('languageToggle').click()");check("\"zh\"".equals(js(activity,"JSON.parse(LocalApp.getLanguage()).language")),"language writes to native SQLite");js(activity,"document.getElementById('languageToggle').click()");
   js(activity,"document.getElementById('navData').click();document.getElementById('theme').value='dark';document.getElementById('theme').dispatchEvent(new Event('change'))");check("\"dark\"".equals(js(activity,"JSON.parse(LocalApp.getState()).preferences.theme")),"theme writes to native SQLite");
   js(activity,"document.getElementById('navHome').click();document.querySelector('.record').click();document.getElementById('delete').click();document.getElementById('acceptConfirm').click()");check("1".equals(js(activity,"JSON.parse(LocalApp.getState()).trash.length")),"real delete moves to native trash");
   js(activity,"document.getElementById('undo').click()");check("2".equals(js(activity,"JSON.parse(LocalApp.list()).records.length")),"real undo restores native record");
   js(activity,"document.querySelector('.record').click();document.getElementById('delete').click();document.getElementById('acceptConfirm').click();LocalApp.removeTrash('*');LocalApp.setPreferences(JSON.stringify({theme:'system',font:'system',animation:true}))");
   check("1".equals(js(activity,"JSON.parse(LocalApp.list()).records.length")),"test cleanup retains legacy fixture");
  }finally{runOnMainSync(()->activity.finish());}
 }

 @Override public void onStart(){Bundle result=new Bundle();try{if("seed".equals(mode))seed();else if("persist".equals(mode)){SQLiteDatabase d=SQLiteDatabase.openOrCreateDatabase(new File(getTargetContext().getNoBackupFilesDir(),"overtime.db"),null);OvertimeStore st=new OvertimeStore(d);check(st.records().getJSONObject(0).getString("id").equals("upgrade-fixture"),"fixture persisted after restart");d.close();}else {tests();v13();webChecks();}result.putString("stream","PASS: "+mode+"; "+count+" native assertions\n");finish(Activity.RESULT_OK,result);}catch(Throwable e){result.putString("stream","FAIL: "+e.toString()+"\n"+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
