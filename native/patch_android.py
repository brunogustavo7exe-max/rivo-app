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
print('Patch Android aplicado.')
