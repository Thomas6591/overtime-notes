package cn.lan.overtime;

import android.app.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import java.io.File;
import java.time.*;
import org.json.*;

/** Local, inexact reminders; no network, service or exact-alarm permission. */
public class ReminderReceiver extends BroadcastReceiver {
 static final String CHANNEL="overtime_reminder";
 static final String SNOOZE="cn.lan.overtime.SNOOZE",FIRE="cn.lan.overtime.REMIND";
 static PendingIntent intent(Context c,int code,String action,String date){return PendingIntent.getBroadcast(c,code,new Intent(c,ReminderReceiver.class).setAction(action).putExtra("date",date),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static void schedule(Context c,OvertimeStore store)throws Exception{
  AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);a.cancel(intent(c,10,FIRE,""));
  JSONObject p=OvertimeStore.validReminder(new JSONObject(store.getSetting("reminder","{}")));
  if(!p.getBoolean("enabled")){a.cancel(intent(c,11,FIRE,""));return;}
  ZonedDateTime now=ZonedDateTime.now();JSONArray days=p.getJSONArray("days");LocalTime time=LocalTime.parse(p.getString("time"));
  for(int i=0;i<8;i++){LocalDate day=now.toLocalDate().plusDays(i);boolean selected=false;for(int j=0;j<days.length();j++)if(days.getInt(j)==day.getDayOfWeek().getValue())selected=true;
   ZonedDateTime next=day.atTime(time).atZone(now.getZone());if(selected&&next.isAfter(now)){a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),intent(c,10,FIRE,day.toString()));return;}}
 }
 static boolean permitted(Context c){NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);NotificationChannel ch=n.getNotificationChannel(CHANNEL);return n.areNotificationsEnabled()&&(ch==null||ch.getImportance()!=NotificationManager.IMPORTANCE_NONE);}
 static boolean shouldNotify(OvertimeStore s,LocalDate date)throws Exception{JSONObject p=OvertimeStore.validReminder(new JSONObject(s.getSetting("reminder","{}")));if(!p.getBoolean("enabled"))return false;boolean selected=false;JSONArray days=p.getJSONArray("days");for(int i=0;i<days.length();i++)if(days.getInt(i)==date.getDayOfWeek().getValue())selected=true;if(!selected)return false;JSONArray rows=s.records();for(int i=0;i<rows.length();i++)if(date.toString().equals(rows.getJSONObject(i).getString("date")))return false;return true;}
 @Override public void onReceive(Context c,Intent in){
  SQLiteDatabase db=null;try{db=SQLiteDatabase.openOrCreateDatabase(new File(c.getNoBackupFilesDir(),"overtime.db"),null);OvertimeStore s=new OvertimeStore(db);schedule(c,s);
   if(SNOOZE.equals(in.getAction())){JSONObject p=OvertimeStore.validReminder(new JSONObject(s.getSetting("reminder","{}")));if(p.getBoolean("enabled")){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+1800000,intent(c,11,FIRE,LocalDate.now().toString()));}((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(13);return;}
   if(!FIRE.equals(in.getAction())||!LocalDate.now().toString().equals(in.getStringExtra("date"))||!shouldNotify(s,LocalDate.now())||!permitted(c))return;
   String lang=s.getSetting("language",java.util.Locale.getDefault().getLanguage().equals("zh")?"zh":"en");boolean zh="zh".equals(lang);NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);n.createNotificationChannel(new NotificationChannel(CHANNEL,zh?"加班漏记提醒":"Overtime reminders",NotificationManager.IMPORTANCE_DEFAULT));
   PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification notification=new Notification.Builder(c,CHANNEL).setSmallIcon(c.getResources().getIdentifier("ic_launcher","drawable",c.getPackageName())).setContentTitle(zh?"今天的加班记了吗？":"Have you logged today's overtime?").setContentText(zh?"今天还没有记录，点击打开加班手记。":"No entry today. Tap to open Overtime Notes.").setContentIntent(open).setAutoCancel(true).addAction(new Notification.Action.Builder((android.graphics.drawable.Icon)null,zh?"30分钟后提醒":"Remind in 30 minutes",intent(c,12,SNOOZE,"")).build()).build();n.notify(13,notification);
  }catch(Exception ignored){/* A reminder must never interrupt record storage. */}finally{if(db!=null)db.close();}
 }
}
