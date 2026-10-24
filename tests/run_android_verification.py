"""Run a local SDK emulator, install old/new signed APKs, and verify native storage.
Requires the Android emulator, platform-tools and an API 28 x86_64 image.
Set OVERTIME_TEST_SDK and OVERTIME_TEST_IMAGE to your local SDK/image paths.
"""
import os,pathlib,subprocess,time,sys,xml.etree.ElementTree as E
root=pathlib.Path(__file__).resolve().parents[1]
sdk=pathlib.Path(os.environ.get('OVERTIME_TEST_SDK',str(root/'.cache/emulator-tools')))
image=pathlib.Path(os.environ.get('OVERTIME_TEST_IMAGE',str(sdk/'x86_64')))
name='OvertimeTest-'+str(int(time.time()))
avd=sdk/(name+'.avd');avd.mkdir(exist_ok=True);(sdk/(name+'.ini')).write_text('avd.ini.encoding=UTF-8\npath='+str(avd)+'\ntarget=android-28\n')
(avd/'config.ini').write_text('avd.name=OvertimeTest\nhw.cpu.arch=x86_64\nhw.cpu.ncore=2\nhw.ramSize=2048\nhw.lcd.width=390\nhw.lcd.height=844\nhw.lcd.density=160\nhw.keyboard=yes\nhw.gpu.enabled=yes\nhw.gpu.mode=swiftshader_indirect\nhw.audioInput=no\nhw.audioOutput=no\ndisk.dataPartition.size=1500M\nfastboot.forceColdBoot=yes\nshowDeviceFrame=no\n')
adb=sdk/'platform-tools/adb'
def command(*args,timeout=60):
 r=subprocess.run([str(adb),'-s','emulator-5554',*map(str,args)],capture_output=True,text=True,timeout=timeout)
 if r.returncode:raise RuntimeError(r.stdout+r.stderr)
 return r.stdout.strip()
env=os.environ.copy();env['ANDROID_AVD_HOME']=str(sdk);env['ANDROID_HOME']=str(sdk);env['ANDROID_I_WANT_MY_TCG']='yes'
subprocess.run([str(adb),'start-server'],check=True)
log=(root/'build/android-emulator.log').open('w')
emulator=subprocess.Popen([str(sdk/'emulator/emulator'),'-avd',name,'-sysdir',str(image),'-no-window','-no-audio','-no-snapshot','-no-boot-anim','-no-metrics','-accel','off','-gpu','swiftshader_indirect','-port','5554'],env=env,stdout=log,stderr=subprocess.STDOUT)
try:
 deadline=time.monotonic()+600;last=0
 while time.monotonic()<deadline:
  if emulator.poll() is not None:raise RuntimeError('Emulator exited; inspect build/android-emulator.log')
  try:
   if command('shell','getprop','sys.boot_completed',timeout=8)=='1':break
  except Exception:pass
  if time.monotonic()-last>20:print('Waiting for Android boot...',flush=True);last=time.monotonic()
  time.sleep(3)
 else:raise RuntimeError('Android boot timed out')
 print('Android booted:',command('shell','getprop','ro.build.version.release'),flush=True)
 
 try:command('uninstall','cn.lan.overtime')
 except Exception:pass
 print('Old install:',command('install','-r',root/'downloads/overtime-notes-1.1.0.apk'),flush=True)
 print('Test install:',command('install','-r',root/'build/native-tests/tests.apk'),flush=True)
 seed=command('shell','am','instrument','-w','-e','mode','seed','cn.lan.overtime.tests/cn.lan.overtime.StoreInstrumentation',timeout=90);print(seed,flush=True)
 if 'PASS: seed' not in seed:raise RuntimeError('Could not prepare v1 upgrade fixture')
 print('Upgrade install:',command('install','-r',root/'build/overtime-notes-1.2.0.apk'),flush=True)
 result=command('shell','am','instrument','-w','-e','mode','tests','cn.lan.overtime.tests/cn.lan.overtime.StoreInstrumentation',timeout=300);print(result,flush=True)
 (root/'build/android-native-results.txt').write_text(seed+'\n'+result+'\n')
 if 'PASS: tests' not in result:raise RuntimeError('Native storage verification failed')
 command('shell','am','start','-n','cn.lan.overtime/.MainActivity')
 ui_deadline=time.monotonic()+150
 while time.monotonic()<ui_deadline:
  time.sleep(6)
  command('shell','uiautomator','dump','/sdcard/overtime-ui.xml',timeout=30)
  hierarchy=command('shell','cat','/sdcard/overtime-ui.xml');(root/'build/android-ui.xml').write_text(hierarchy)
  if 'Overtime Notes' in hierarchy:break
  print('Waiting for embedded Android WebView...',flush=True)
 else:raise RuntimeError('App interface did not load')
 print('PASS: release app launch and WebView accessibility tree',flush=True)
 screenshot=subprocess.run([str(adb),'-s','emulator-5554','exec-out','screencap','-p'],capture_output=True,check=True).stdout
 (root/'build/android-release-home.png').write_bytes(screenshot)
 # Restart the app to ensure the real bridge reads persisted records.
 command('shell','am','force-stop','cn.lan.overtime');command('shell','am','start','-n','cn.lan.overtime/.MainActivity')
 restart_deadline=time.monotonic()+150
 while time.monotonic()<restart_deadline:
  time.sleep(6)
  command('shell','uiautomator','dump','/sdcard/overtime-ui.xml',timeout=30)
  hierarchy=command('shell','cat','/sdcard/overtime-ui.xml')
  if '2.5' in hierarchy and 'Overtime Notes' in hierarchy:break
  print('Waiting for restarted Android WebView...',flush=True)
 else:raise RuntimeError('Persisted 2.5-hour upgrade fixture missing after restart')
 print('PASS: app restart retains legacy record and English language',flush=True)
 print('ALL ANDROID CHECKS PASSED',flush=True)
finally:
 try:
  (root/'build/android-logcat.txt').write_text(command('logcat','-d','-t','2000',timeout=20))
  (root/'build/android-last-screen.png').write_bytes(subprocess.run([str(adb),'-s','emulator-5554','exec-out','screencap','-p'],capture_output=True,timeout=20).stdout)
 except Exception:pass
 try:command('emu','kill',timeout=10)
 except Exception:emulator.terminate()
 try:emulator.wait(timeout=20)
 except subprocess.TimeoutExpired:emulator.kill()
 log.close()
