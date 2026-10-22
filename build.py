#!/usr/bin/env python3
"""Build on Linux with Java 17 and Python 3; never require a published private key."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / '.cache' / 'android-tools'
BUILD = ROOT / 'build'
DOWNLOADS = {
    'platform.zip': ('https://dl.google.com/android/repository/platform-35_r02.zip', '0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0'),
    'build-tools.zip': ('https://dl.google.com/android/repository/build-tools_r35_linux.zip', 'bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88'),
    'ecj.jar': ('https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.33.0/ecj-3.33.0.jar', 'f7686c4960cf70c2ebc5c500a73a8cfc04541b730c18f1c5c21329889b137f45'),
}


def run(*args, **kwargs):
    subprocess.run([str(a) for a in args], check=True, **kwargs)


def signing_config(release=False):
    if release:
        required = ['OVERTIME_KEYSTORE', 'OVERTIME_KEY_ALIAS', 'OVERTIME_KEYSTORE_PASSWORD']
        missing = [name for name in required if not os.environ.get(name)]
        if missing:
            raise ValueError('Release signing requires: ' + ', '.join(missing))
        keystore = Path(os.environ['OVERTIME_KEYSTORE']).expanduser().resolve()
        if not keystore.is_file():
            raise ValueError('OVERTIME_KEYSTORE must point to an existing private keystore.')
        return keystore, os.environ['OVERTIME_KEY_ALIAS'], os.environ['OVERTIME_KEYSTORE_PASSWORD'], os.environ.get('OVERTIME_KEY_PASSWORD', os.environ['OVERTIME_KEYSTORE_PASSWORD'])
    folder = ROOT / '.local-signing'
    folder.mkdir(exist_ok=True, mode=0o700)
    config = folder / 'development.json'
    keystore = folder / 'development.p12'
    if not config.exists():
        if keystore.exists():
            raise ValueError('Development keystore exists without its local configuration; restore its configuration or choose a new local key.')
        password = secrets.token_urlsafe(32)
        env = os.environ.copy()
        env['OVERTIME_DEVELOPMENT_PASSWORD'] = password
        # The password is supplied through the child environment, never the command line.
        run('keytool', '-genkeypair', '-keystore', keystore, '-storetype', 'PKCS12', '-storepass:env', 'OVERTIME_DEVELOPMENT_PASSWORD', '-keypass:env', 'OVERTIME_DEVELOPMENT_PASSWORD', '-alias', 'overtime-development', '-keyalg', 'RSA', '-keysize', '3072', '-sigalg', 'SHA256withRSA', '-validity', '3650', '-dname', 'CN=Local Development, O=Overtime Notes', env=env)
        keystore.chmod(0o600)
        config.write_text(json.dumps({'alias': 'overtime-development', 'password': password}))
        config.chmod(0o600)
    if not keystore.is_file():
        raise ValueError('The local development keystore is missing. Restore it before rebuilding compatible updates.')
    values = json.loads(config.read_text())
    return keystore, values['alias'], values['password'], values['password']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--release', action='store_true', help='Sign using private environment-provided release credentials.')
    args = parser.parse_args()
    # Check signing configuration before downloading tools or overwriting build output.
    key, alias, store_password, key_password = signing_config(args.release)
    from scripts.generate_locales import generate
    generate(ROOT)
    TOOLS.mkdir(exist_ok=True, parents=True)
    for name, (url, expected) in DOWNLOADS.items():
        target = TOOLS / name
        if not target.exists():
            print('Downloading', name, flush=True)
            temporary = target.with_suffix(target.suffix + '.part')
            urllib.request.urlretrieve(url, temporary)
            if hashlib.sha256(temporary.read_bytes()).hexdigest() != expected:
                temporary.unlink()
                raise ValueError('Tool checksum mismatch: ' + name)
            temporary.replace(target)
        if hashlib.sha256(target.read_bytes()).hexdigest() != expected:
            raise ValueError('Tool checksum mismatch: ' + name)
    for name, folder in [('platform.zip', 'android-35'), ('build-tools.zip', 'android-15')]:
        if not (TOOLS / folder).exists():
            with zipfile.ZipFile(TOOLS / name) as archive:
                archive.extractall(TOOLS)
    bt = TOOLS / 'android-15'
    android = TOOLS / 'android-35/android.jar'
    for name in ['aapt2', 'zipalign']:
        (bt / name).chmod(0o755)
    if BUILD.exists():
        shutil.rmtree(BUILD)
    classes, dex = BUILD / 'classes', BUILD / 'dex'
    classes.mkdir(parents=True)
    dex.mkdir()
    sources = sorted((ROOT / 'app/src/main/java').rglob('*.java'))
    run('java', '-jar', TOOLS / 'ecj.jar', '-1.8', '-proc:none', '-nowarn', '-bootclasspath', str(android) + os.pathsep + str(bt / 'core-lambda-stubs.jar'), '-d', classes, *sources)
    run(bt / 'aapt2', 'compile', '--dir', ROOT / 'app/src/main/res', '-o', BUILD / 'res.zip')
    run(bt / 'aapt2', 'link', '-o', BUILD / 'base.apk', '-I', android, '--manifest', ROOT / 'app/src/main/AndroidManifest.xml', '-A', ROOT / 'app/src/main/assets', BUILD / 'res.zip')
    run('java', '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--lib', android, '--min-api', '26', '--output', dex, *sorted(classes.rglob('*.class')))
    with zipfile.ZipFile(BUILD / 'base.apk', 'a', zipfile.ZIP_DEFLATED) as archive:
        for path in dex.glob('*.dex'):
            archive.write(path, path.name)
    run(bt / 'zipalign', '-f', '-p', '4', BUILD / 'base.apk', BUILD / 'aligned.apk')
    env = os.environ.copy()
    env['OVERTIME_SIGN_STORE_PASSWORD'] = store_password
    env['OVERTIME_SIGN_KEY_PASSWORD'] = key_password
    suffix = '' if args.release else '-development'
    output = BUILD / ('overtime-notes-1.1.0' + suffix + '.apk')
    run('java', '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', key, '--ks-key-alias', alias, '--ks-pass', 'env:OVERTIME_SIGN_STORE_PASSWORD', '--key-pass', 'env:OVERTIME_SIGN_KEY_PASSWORD', '--v4-signing-enabled', 'false', '--out', output, BUILD / 'aligned.apk', env=env)
    run('java', '-jar', bt / 'lib/apksigner.jar', 'verify', '--verbose', output)
    print('Built:', output)


if __name__ == '__main__':
    main()
