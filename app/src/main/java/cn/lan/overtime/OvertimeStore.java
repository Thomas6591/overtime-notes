package cn.lan.overtime;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Owns all mutations; existing v1 records/settings tables and payloads stay intact. */
final class OvertimeStore {
 static final long RETENTION=30L*86400000;
 private final SQLiteDatabase db;
 OvertimeStore(SQLiteDatabase database){
  db=database;
  db.execSQL("CREATE TABLE IF NOT EXISTS records (id TEXT PRIMARY KEY, payload TEXT NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS trash (id TEXT PRIMARY KEY, payload TEXT NOT NULL, deleted_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS templates (id TEXT PRIMARY KEY, payload TEXT NOT NULL)");
  db.execSQL("CREATE INDEX IF NOT EXISTS records_interval ON records(start_ms,end_ms)");
 }
 static long start(JSONObject r)throws Exception{return TimeMath.start(r.getString("date"),r.getString("start"));}
 static long end(JSONObject r)throws Exception{return TimeMath.end(r.getString("date"),r.getString("end"),r.getBoolean("nextDay"));}
 static JSONObject normalize(JSONObject r)throws Exception{
  String date=r.getString("date"),start=r.getString("start"),end=r.getString("end"),type=r.getString("type"),note=r.getString("note");
  if(!(r.get("nextDay") instanceof Boolean))throw new IllegalArgumentException("次日标记格式不正确");
  Object br=r.get("break");if(!(br instanceof Number)||((Number)br).doubleValue()!=((Number)br).intValue())throw new IllegalArgumentException("休息时长须为整数分钟");int rest=((Number)br).intValue();
  TimeMath.minutes(TimeMath.start(date,start),TimeMath.end(date,end,r.getBoolean("nextDay")),rest);
  if(!Arrays.asList("工作日","休息日","节假日").contains(type))throw new IllegalArgumentException("加班类型不正确");if(note.length()>500)throw new IllegalArgumentException("备注最多500个字");
  String id=r.optString("id","");if(id.isEmpty())id=UUID.randomUUID().toString();if(!id.matches("[a-zA-Z0-9-]{1,64}"))throw new IllegalArgumentException("记录编号不正确");
  JSONObject out=new JSONObject();out.put("id",id);out.put("date",date);out.put("start",start);out.put("end",end);out.put("nextDay",r.getBoolean("nextDay"));out.put("break",rest);out.put("type",type);out.put("note",note);return out;
 }
 private boolean contains(String table,String id){try(Cursor c=db.rawQuery("SELECT id FROM "+table+" WHERE id=?",new String[]{id})){return c.moveToFirst();}}
 private JSONObject get(String table,String id)throws Exception{try(Cursor c=db.rawQuery("SELECT payload FROM "+table+" WHERE id=?",new String[]{id})){if(!c.moveToFirst())throw new IllegalArgumentException("记录已不存在，请返回后重试");return new JSONObject(c.getString(0));}}
 private boolean overlaps(JSONObject r)throws Exception{try(Cursor c=db.rawQuery("SELECT id FROM records WHERE start_ms < ? AND end_ms > ? AND id != ? LIMIT 1",new String[]{""+end(r),""+start(r),r.getString("id")})){return c.moveToFirst();}}
 private void write(JSONObject r)throws Exception{ContentValues v=new ContentValues();v.put("id",r.getString("id"));v.put("payload",r.toString());v.put("start_ms",start(r));v.put("end_ms",end(r));if(db.insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IOException("write failed");}
 synchronized JSONArray records()throws Exception{JSONArray a=new JSONArray();try(Cursor c=db.rawQuery("SELECT payload FROM records ORDER BY start_ms DESC",null)){while(c.moveToNext())a.put(new JSONObject(c.getString(0)));}return a;}
 private void purge(){db.delete("trash","deleted_at<=?",new String[]{""+(System.currentTimeMillis()-RETENTION)});}
 synchronized JSONArray trash()throws Exception{purge();JSONArray a=new JSONArray();try(Cursor c=db.rawQuery("SELECT payload,deleted_at FROM trash ORDER BY deleted_at DESC",null)){while(c.moveToNext()){JSONObject o=new JSONObject();o.put("record",new JSONObject(c.getString(0)));o.put("deletedAt",c.getLong(1));o.put("expiresAt",c.getLong(1)+RETENTION);a.put(o);}}return a;}
 synchronized JSONArray templates()throws Exception{JSONArray a=new JSONArray();try(Cursor c=db.rawQuery("SELECT payload FROM templates ORDER BY rowid",null)){while(c.moveToNext())a.put(new JSONObject(c.getString(0)));}return a;}
 synchronized String getSetting(String key,String fallback){try(Cursor c=db.rawQuery("SELECT value FROM settings WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):fallback;}}
 synchronized void setSetting(String key,String value)throws IOException{ContentValues v=new ContentValues();v.put("key",key);v.put("value",value);if(db.insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IOException("setting write failed");}
 synchronized JSONObject preferences()throws Exception{return validPreferences(new JSONObject(getSetting("preferences","{}")));}
 private JSONObject validPreferences(JSONObject p)throws Exception{String theme=p.optString("theme","system"),font=p.optString("font","system");if(!Arrays.asList("system","light","dark","soft").contains(theme)||!Arrays.asList("system","large").contains(font)||(p.has("animation")&&!(p.opt("animation") instanceof Boolean)))throw new IllegalArgumentException("error.preferences");JSONObject out=new JSONObject();out.put("theme",theme);out.put("font",font);out.put("animation",p.optBoolean("animation",true));return out;}
 synchronized void preferences(JSONObject p)throws Exception{setSetting("preferences",validPreferences(p).toString());}
 synchronized JSONObject state()throws Exception{JSONObject o=new JSONObject();o.put("records",records());o.put("trash",trash());o.put("templates",templates());o.put("preferences",preferences());o.put("lastBackup",getSetting("lastBackup",""));return o;}
 synchronized JSONObject save(JSONObject input)throws Exception{String oldId=input.optString("id","");if(!oldId.isEmpty()&&!contains("records",oldId))throw new IllegalArgumentException("记录已不存在，请返回后重试");JSONObject r=normalize(input);if(overlaps(r))throw new IllegalArgumentException("这段时间与已有记录重叠，请检查日期和时间");write(r);return r;}
 synchronized JSONArray batch(JSONArray list)throws Exception{
  if(list.length()<1||list.length()>62)throw new IllegalArgumentException("batch.datesError");JSONArray result=new JSONArray();db.beginTransaction();try{for(int i=0;i<list.length();i++){JSONObject row=list.getJSONObject(i),out=new JSONObject();out.put("date",row.optString("date"));try{row.put("id","");JSONObject r=normalize(row);if(overlaps(r))throw new IllegalArgumentException("这段时间与已有记录重叠，请检查日期和时间");write(r);out.put("ok",true);}catch(IllegalArgumentException|JSONException e){out.put("ok",false);out.put("message",e.getMessage());}result.put(out);}db.setTransactionSuccessful();}finally{db.endTransaction();}return result;
 }
 synchronized void delete(String id)throws Exception{JSONObject r=get("records",id);db.beginTransaction();try{ContentValues v=new ContentValues();v.put("id",id);v.put("payload",r.toString());v.put("deleted_at",System.currentTimeMillis());if(db.insertWithOnConflict("trash",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IOException();db.delete("records","id=?",new String[]{id});db.setTransactionSuccessful();}finally{db.endTransaction();}}
 synchronized void restore(String id)throws Exception{purge();JSONObject r=normalize(get("trash",id));if(contains("records",id)||overlaps(r))throw new IllegalArgumentException("trash.conflict");db.beginTransaction();try{write(r);db.delete("trash","id=?",new String[]{id});db.setTransactionSuccessful();}finally{db.endTransaction();}}
 synchronized void removeTrash(String id){if("*".equals(id))db.delete("trash",null,null);else db.delete("trash","id=?",new String[]{id});}
 private JSONObject normalizeTemplate(JSONObject input)throws Exception{String name=input.getString("name").trim(),id=input.optString("id","");if(name.isEmpty()||name.length()>80)throw new IllegalArgumentException("template.nameError");if(id.isEmpty())id=UUID.randomUUID().toString();if(!id.matches("[a-zA-Z0-9-]{1,64}"))throw new IllegalArgumentException("记录编号不正确");JSONObject source=new JSONObject(input.getJSONObject("record").toString());source.put("date","2000-01-01");source.put("id","");JSONObject out=new JSONObject();out.put("id",id);out.put("name",name);out.put("record",normalize(source));return out;}
 private void writeTemplate(JSONObject t)throws Exception{ContentValues v=new ContentValues();v.put("id",t.getString("id"));v.put("payload",t.toString());if(db.insertWithOnConflict("templates",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IOException();}
 synchronized void saveTemplate(JSONObject t)throws Exception{JSONObject n=normalizeTemplate(t);if(!contains("templates",n.getString("id"))&&templates().length()>=30)throw new IllegalArgumentException("template.limit");writeTemplate(n);}
 synchronized void deleteTemplate(String id){db.delete("templates","id=?",new String[]{id});}
 synchronized JSONObject backup()throws Exception{JSONObject b=new JSONObject();b.put("app","cn.lan.overtime");b.put("version",2);b.put("records",records());b.put("trash",trash());b.put("templates",templates());b.put("preferences",preferences());b.put("language",getSetting("language",""));return b;}
 JSONObject validateBackup(JSONObject b)throws Exception{
  if(!"cn.lan.overtime".equals(b.optString("app"))||!(b.opt("version") instanceof Number)||(b.getInt("version")!=1&&b.getInt("version")!=2)||b.getDouble("version")!=b.getInt("version"))throw new IllegalArgumentException("请选择加班手记导出的原始备份文件");
  JSONArray a=b.getJSONArray("records");if(a.length()>10000)throw new IllegalArgumentException("单个备份最多10000条记录");JSONArray checked=new JSONArray();for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);if(r.optString("id","").isEmpty())throw new IllegalArgumentException("备份缺少记录编号");checked.put(normalize(r));}
  JSONObject out=new JSONObject();out.put("records",checked);JSONArray ts=new JSONArray(),tr=new JSONArray();
  if(b.getInt("version")==2){JSONArray input=b.getJSONArray("templates");if(input.length()>30)throw new IllegalArgumentException("template.limit");for(int i=0;i<input.length();i++)ts.put(normalizeTemplate(input.getJSONObject(i)));JSONArray trash=b.getJSONArray("trash");if(trash.length()>10000)throw new IllegalArgumentException("单个备份最多10000条记录");for(int i=0;i<trash.length();i++){JSONObject entry=trash.getJSONObject(i),record=entry.getJSONObject("record");if(record.optString("id","").isEmpty())throw new IllegalArgumentException("备份缺少记录编号");Object rawTime=entry.get("deletedAt");if(!(rawTime instanceof Number))throw new IllegalArgumentException("error.format");long time=((Number)rawTime).longValue();if(((Number)rawTime).doubleValue()!=time||time<0||time>System.currentTimeMillis()+300000)throw new IllegalArgumentException("error.format");JSONObject n=new JSONObject();n.put("record",normalize(record));n.put("deletedAt",time);tr.put(n);}}
  out.put("templates",ts);out.put("trash",tr);if(b.getInt("version")==2){out.put("preferences",validPreferences(b.getJSONObject("preferences")));String lang=b.optString("language","");if(!lang.isEmpty()&&!Arrays.asList("zh","en").contains(lang))throw new IllegalArgumentException("error.format");out.put("language",lang);}return out;
 }
 private static final class Plan {JSONArray accepted=new JSONArray();int duplicates=0,conflicts=0;}
 private Plan plan(JSONObject checked)throws Exception{
  Plan p=new Plan();Set<String> ids=new HashSet<>();TreeMap<Long,Long> intervals=new TreeMap<>();try(Cursor c=db.rawQuery("SELECT id,start_ms,end_ms FROM records",null)){while(c.moveToNext()){ids.add(c.getString(0));intervals.put(c.getLong(1),c.getLong(2));}}try(Cursor c=db.rawQuery("SELECT id FROM trash",null)){while(c.moveToNext())ids.add(c.getString(0));}
  JSONArray a=checked.getJSONArray("records");for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);String id=r.getString("id");if(ids.contains(id)){p.duplicates++;continue;}long start=start(r),end=end(r);Map.Entry<Long,Long> lo=intervals.floorEntry(start),hi=intervals.ceilingEntry(start);if((lo!=null&&lo.getValue()>start)||(hi!=null&&hi.getKey()<end)){p.conflicts++;continue;}ids.add(id);intervals.put(start,end);p.accepted.put(r);}return p;
 }
 synchronized JSONObject preview(JSONObject checked)throws Exception{purge();Plan p=plan(checked);JSONObject out=new JSONObject();out.put("added",p.accepted.length());out.put("duplicates",p.duplicates);out.put("conflicts",p.conflicts);out.put("templates",checked.getJSONArray("templates").length());out.put("trash",checked.getJSONArray("trash").length());out.put("hasPreferences",checked.has("preferences"));return out;}
 synchronized JSONObject importBackup(JSONObject checked,boolean restoreSettings)throws Exception{
  db.beginTransaction();try{purge();Plan p=plan(checked);for(int i=0;i<p.accepted.length();i++)write(p.accepted.getJSONObject(i));int templatesAdded=0,trashAdded=0;JSONArray ts=checked.getJSONArray("templates"),tr=checked.getJSONArray("trash");int templateCount=templates().length();for(int i=0;i<ts.length();i++){JSONObject t=ts.getJSONObject(i);if(!contains("templates",t.getString("id"))&&templateCount<30){writeTemplate(t);templateCount++;templatesAdded++;}}for(int i=0;i<tr.length();i++){JSONObject entry=tr.getJSONObject(i),r=entry.getJSONObject("record");String id=r.getString("id");long deletedAt=entry.getLong("deletedAt");if(deletedAt<=System.currentTimeMillis()-RETENTION||contains("records",id)||contains("trash",id))continue;ContentValues v=new ContentValues();v.put("id",id);v.put("payload",r.toString());v.put("deleted_at",deletedAt);if(db.insert("trash",null,v)==-1)throw new IOException();trashAdded++;}JSONObject result=new JSONObject();result.put("added",p.accepted.length());result.put("duplicates",p.duplicates);result.put("conflicts",p.conflicts);result.put("templatesAdded",templatesAdded);result.put("trashAdded",trashAdded);if(restoreSettings&&checked.has("preferences")){preferences(checked.getJSONObject("preferences"));String lang=checked.optString("language","");if(!lang.isEmpty())setSetting("language",lang);}result.put("language",getSetting("language",""));db.setTransactionSuccessful();return result;}finally{db.endTransaction();}
 }
}
