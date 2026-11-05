"""Roda no workflow depois de 'npx cap add android': liga o Firebase, o serviço do alerta e a tela cheia."""
import re

# 1) AndroidManifest: serviços e MainActivity por cima da tela de bloqueio
p = 'android/app/src/main/AndroidManifest.xml'
s = open(p, encoding='utf-8').read()
svc = '''
        <service android:name=".RivoMessagingService" android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
        <service android:name=".AlertService" android:exported="false" android:foregroundServiceType="mediaPlayback" />
'''
if 'RivoMessagingService' not in s:
    s = s.replace('</application>', svc + '    </application>', 1)
if 'default_notification_icon' not in s:
    meta = '''
        <meta-data android:name="com.google.firebase.messaging.default_notification_icon" android:resource="@drawable/ic_stat_rivo" />
        <meta-data android:name="com.google.firebase.messaging.default_notification_color" android:resource="@color/rivo_ambar" />
'''
    s = s.replace('</application>', meta + '    </application>', 1)
if 'showWhenLocked' not in s:
    s, n = re.subn(r'<activity(?=[^>]*MainActivity)', '<activity android:showWhenLocked="true" android:turnScreenOn="true"', s, count=1)
    assert n == 1, 'MainActivity não encontrada no AndroidManifest'
open(p, 'w', encoding='utf-8').write(s)

# 2) Gradle: Firebase Messaging + plugin google-services
g = 'android/build.gradle'
s = open(g, encoding='utf-8').read()
if 'google-services' not in s:
    s = s.replace('dependencies {', "dependencies {\n        classpath 'com.google.gms:google-services:4.4.2'", 1)
    open(g, 'w', encoding='utf-8').write(s)

a = 'android/app/build.gradle'
s = open(a, encoding='utf-8').read()
if 'firebase-messaging' not in s:
    s, n = re.subn(r'^dependencies \{', "dependencies {\n    implementation platform('com.google.firebase:firebase-bom:33.5.1')\n    implementation 'com.google.firebase:firebase-messaging'", s, count=1, flags=re.M)
    assert n == 1, 'bloco dependencies não encontrado no app/build.gradle'
if 'com.google.gms.google-services' not in s:
    s += "\napply plugin: 'com.google.gms.google-services'\n"
open(a, 'w', encoding='utf-8').write(s)

# 3) FileProvider: deixa o app abrir o APK baixado no instalador do Android
import os
m = open('android/app/src/main/AndroidManifest.xml', encoding='utf-8').read()
if 'fileprovider' not in m.lower():
    prov = '''        <provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
        </provider>
'''
    m = m.replace('</application>', prov + '    </application>', 1)
    open('android/app/src/main/AndroidManifest.xml', 'w', encoding='utf-8').write(m)
os.makedirs('android/app/src/main/res/xml', exist_ok=True)
open('android/app/src/main/res/xml/file_paths.xml', 'w', encoding='utf-8').write('<?xml version="1.0" encoding="utf-8"?>\n<paths xmlns:android="http://schemas.android.com/apk/res/android">\n    <external-path name="my_images" path="." />\n    <cache-path name="my_cache_images" path="." />\n</paths>\n')
print('Patch Android aplicado.')

# cor âmbar usada nas notificações
os.makedirs('android/app/src/main/res/values', exist_ok=True)
open('android/app/src/main/res/values/rivo_cores.xml', 'w').write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="rivo_ambar">#FFB800</color>\n</resources>\n')
