"""Build the native instrumentation APK with the same private signing env as release builds."""
from pathlib import Path
import os,sys,zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import build
root=build.ROOT;bt=build.TOOLS/'android-15';android=build.TOOLS/'android-35/android.jar';out=build.BUILD/'native-tests';out.mkdir(parents=True,exist_ok=True);classes=out/'classes';classes.mkdir(exist_ok=True);dex=out/'dex';dex.mkdir(exist_ok=True)
build.run('java','-jar',build.TOOLS/'ecj.jar','-1.8','-proc:none','-nowarn','-bootclasspath',str(android)+os.pathsep+str(bt/'core-lambda-stubs.jar'),'-classpath',build.BUILD/'classes','-d',classes,root/'tests/android/StoreInstrumentation.java')
build.run(bt/'aapt2','link','-o',out/'base.apk','-I',android,'--manifest',root/'tests/android/AndroidManifest.xml')
build.run('java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--classpath',build.BUILD/'classes','--min-api','26','--output',dex,*classes.rglob('*.class'))
with zipfile.ZipFile(out/'base.apk','a',zipfile.ZIP_DEFLATED) as z:
 for p in dex.glob('*.dex'):z.write(p,p.name)
build.run(bt/'zipalign','-f','4',out/'base.apk',out/'aligned.apk')
key,alias,password,keypass=build.signing_config(True);env=os.environ.copy();env['OVERTIME_SIGN_STORE_PASSWORD']=password;env['OVERTIME_SIGN_KEY_PASSWORD']=keypass
build.run('java','-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias',alias,'--ks-pass','env:OVERTIME_SIGN_STORE_PASSWORD','--key-pass','env:OVERTIME_SIGN_KEY_PASSWORD','--out',out/'tests.apk',out/'aligned.apk',env=env)
print('Built native instrumentation APK')
