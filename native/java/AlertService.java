package com.dcastrolabs.rivo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.util.Locale;

/** Toca o toque escolhido em loop, vibra e mostra a chamada por cima da tela de bloqueio. */
public class AlertService extends Service {
    static final String CANAL = "rivo_alerta_v1";
    static final String ACAO_PARAR = "com.dcastrolabs.rivo.PARAR_ALERTA";
    static final int NOTIF_ID = 7701;
    static final long DURACAO_MS = 45000;

    private MediaPlayer mp;
    private Vibrator vib;
    private int volAntes = -1;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable fim = new Runnable() { public void run() { stopSelf(); } };

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        if (in == null) { stopSelf(); return START_NOT_STICKY; }
        if (ACAO_PARAR.equals(in.getAction())) {
            getSharedPreferences("rivo", MODE_PRIVATE).edit().remove("pend").apply();
            parar();
            stopSelf();
            return START_NOT_STICKY;
        }
        guardarPendente(in);
        criarCanal();
        Notification n = montar(in);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTIF_ID, n);
        } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        tocar();
        h.removeCallbacks(fim);
        h.postDelayed(fim, DURACAO_MS);
        return START_NOT_STICKY;
    }

    private void guardarPendente(Intent in) {
        try {
            JSONObject o = new JSONObject();
            Bundle ex = in.getExtras();
            if (ex != null) for (String k : ex.keySet()) o.put(k, String.valueOf(ex.get(k)));
            o.put("_ts", System.currentTimeMillis());
            getSharedPreferences("rivo", MODE_PRIVATE).edit().putString("pend", o.toString()).apply();
        } catch (Exception e) { /* sem pendente */ }
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel c = new NotificationChannel(CANAL, "Chamadas de entrega", NotificationManager.IMPORTANCE_HIGH);
        c.setDescription("Nova entrega para aceitar");
        c.setSound(null, null);            // o som é tocado pelo serviço, em loop
        c.enableVibration(false);          // a vibração também
        c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(c);
    }

    private static String reais(String centavos) {
        try {
            long v = Long.parseLong(centavos);
            return String.format(new Locale("pt", "BR"), "R$ %d,%02d", v / 100, v % 100);
        } catch (Exception e) { return ""; }
    }

    private Notification montar(Intent in) {
        String loja = in.getStringExtra("loja");
        String taxa = reais(in.getStringExtra("taxa"));
        String km = in.getStringExtra("kmCli");
        String txt = (taxa.isEmpty() ? "Corrida nova" : "Corrida " + taxa)
                + (km != null && !km.isEmpty() ? " · entrega a " + km.replace('.', ',') + " km" : "");

        Intent abrir = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("rivoAlerta", true);
        PendingIntent pi = PendingIntent.getActivity(this, 1, abrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent parar = new Intent(this, AlertService.class).setAction(ACAO_PARAR);
        PendingIntent ps = PendingIntent.getService(this, 2, parar, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(R.drawable.ic_stat_rivo)
                .setColor(0xFFFFB800)
                .setContentTitle("🛵 Nova entrega" + (loja != null && !loja.isEmpty() ? " · " + loja : ""))
                .setContentText(txt)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(true)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .addAction(0, "Ignorar", ps)
                .build();
    }

    @SuppressWarnings("deprecation")
    private void tocar() {
        parar(); // reinicia se já estava tocando (re-alerta)
        android.content.SharedPreferences sp = getSharedPreferences("rivo", MODE_PRIVATE);
        String tom = sp.getString("tom", "alerta");
        int res = getResources().getIdentifier("tom_" + tom, "raw", getPackageName());
        if (res == 0) res = getResources().getIdentifier("tom_alerta", "raw", getPackageName());
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            volAntes = am.getStreamVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        } catch (Exception e) { volAntes = -1; }
        try {
            mp = new MediaPlayer();
            AssetFileDescriptor afd = getResources().openRawResourceFd(res);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setAudioAttributes(aa);
            mp.setLooping(true);
            mp.prepare();
            mp.start();
        } catch (Exception e) { mp = null; }
        try {
            vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] padrao = {0, 600, 250, 600, 250, 900, 500};
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(padrao, 0), aa);
            else vib.vibrate(padrao, 0, aa);
        } catch (Exception e) { /* sem vibração */ }
    }

    private void parar() {
        h.removeCallbacks(fim);
        try { if (mp != null) { mp.stop(); mp.release(); } } catch (Exception e) { /* já parado */ }
        mp = null;
        try { if (vib != null) vib.cancel(); } catch (Exception e) { /* já parado */ }
        try {
            if (volAntes >= 0) {
                ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).setStreamVolume(AudioManager.STREAM_ALARM, volAntes, 0);
                volAntes = -1;
            }
        } catch (Exception e) { /* volume segue como está */ }
    }

    @Override
    public void onDestroy() {
        parar();
        try { stopForeground(true); } catch (Exception e) { /* nada */ }
        super.onDestroy();
    }
}
