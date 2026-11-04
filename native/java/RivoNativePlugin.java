package com.dcastrolabs.rivo;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/** Ponte entre a tela do RIVO (JavaScript) e o Android: push, toque, permissões e login guardado. */
@CapacitorPlugin(name = "RivoNative")
public class RivoNativePlugin extends Plugin {
    static RivoNativePlugin instancia;
    private MediaPlayer previa;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    public void load() { instancia = this; }

    private SharedPreferences sp() { return getContext().getSharedPreferences("rivo", Context.MODE_PRIVATE); }
    private SharedPreferences sec() { return getContext().getSharedPreferences("rivo_sec", Context.MODE_PRIVATE); }

    /** chamado pelo serviço do Firebase quando chega uma corrida e o app está aberto */
    public void emitir(Map<String, String> d) {
        JSObject o = new JSObject();
        for (Map.Entry<String, String> e : d.entrySet()) o.put(e.getKey(), e.getValue());
        notifyListeners("alerta", o);
    }

    @PluginMethod
    public void getToken(final PluginCall c) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(t -> {
            if (t.isSuccessful() && t.getResult() != null) {
                JSObject o = new JSObject();
                o.put("token", t.getResult());
                c.resolve(o);
            } else c.reject("sem token do Firebase");
        });
    }

    @PluginMethod
    public void getTom(PluginCall c) {
        JSObject o = new JSObject();
        o.put("tom", sp().getString("tom", "alerta"));
        c.resolve(o);
    }

    @PluginMethod
    public void setTom(PluginCall c) {
        String tom = c.getString("tom", "alerta");
        if (!tom.matches("[a-z]{3,20}")) { c.reject("toque inválido"); return; }
        sp().edit().putString("tom", tom).apply();
        c.resolve();
    }

    @PluginMethod
    public void preview(PluginCall c) {
        String tom = c.getString("tom", "alerta");
        pararPrevia();
        Context ctx = getContext();
        int res = ctx.getResources().getIdentifier("tom_" + tom, "raw", ctx.getPackageName());
        if (res == 0) { c.reject("toque não encontrado"); return; }
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        previa = MediaPlayer.create(ctx, res, aa, 0);
        if (previa != null) {
            previa.start();
            h.postDelayed(this::pararPrevia, 3500);
        }
        c.resolve();
    }

    private void pararPrevia() {
        try { if (previa != null) { previa.stop(); previa.release(); } } catch (Exception e) { /* já parado */ }
        previa = null;
    }

    @PluginMethod
    public void stopAlert(PluginCall c) {
        pararPrevia();
        try { getContext().stopService(new Intent(getContext(), AlertService.class)); } catch (Exception e) { /* nada */ }
        c.resolve();
    }

    /** entrega ao JavaScript a chamada que fez o app abrir (só se for de menos de 3 minutos) */
    @PluginMethod
    public void consumeAlert(PluginCall c) {
        String s = sp().getString("pend", "");
        sp().edit().remove("pend").apply();
        JSObject o = new JSObject();
        try {
            if (!s.isEmpty()) {
                JSONObject j = new JSONObject(s);
                if (System.currentTimeMillis() - j.optLong("_ts", 0) < 180000) o = new JSObject(s);
            }
        } catch (Exception e) { o = new JSObject(); }
        c.resolve(o);
    }

    /** guarda o login no armazenamento privado do app (o Android não apaga isso junto com o cache da tela) */
    @PluginMethod
    public void setSecure(PluginCall c) {
        String k = c.getString("k", ""), v = c.getString("v", "");
        if (k.isEmpty()) { c.reject("chave vazia"); return; }
        if (v.isEmpty()) sec().edit().remove(k).apply(); else sec().edit().putString(k, v).apply();
        c.resolve();
    }

    @PluginMethod
    public void getSecure(PluginCall c) {
        JSObject o = new JSObject();
        o.put("v", sec().getString(c.getString("k", ""), ""));
        c.resolve(o);
    }

    @PluginMethod
    public void diagnostico(PluginCall c) {
        Context ctx = getContext();
        boolean notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled();
        boolean tela = true;
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            tela = nm.canUseFullScreenIntent();
        }
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        boolean bat = pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        JSObject o = new JSObject();
        o.put("notif", notif);
        o.put("telaCheia", tela);
        o.put("bateria", bat);
        c.resolve(o);
    }

    @PluginMethod
    public void abrir(PluginCall c) {
        String tipo = c.getString("tipo", "");
        Context ctx = getContext();
        String pkg = ctx.getPackageName();
        Intent i;
        if ("notif".equals(tipo)) {
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
        } else if ("telaCheia".equals(tipo) && Build.VERSION.SDK_INT >= 34) {
            i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + pkg));
        } else if ("bateria".equals(tipo)) {
            i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg));
        } else {
            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); c.resolve(); } catch (Exception e) { c.reject("não consegui abrir as configurações"); }
    }

    /** toca um alerta de teste (o do servidor nunca é disparado por aqui) */
    @PluginMethod
    public void testar(PluginCall c) {
        Intent i = new Intent(getContext(), AlertService.class)
                .putExtra("tipo", "entrega").putExtra("loja", "Teste do RIVO").putExtra("taxa", "800")
                .putExtra("kmCli", "3.2").putExtra("rid", "teste").putExtra("chave", "teste");
        try { ContextCompat.startForegroundService(getContext(), i); c.resolve(); } catch (Exception e) { c.reject("o Android não deixou iniciar o teste"); }
    }

    /* ---------- atualização do app: baixa o APK da release e abre o instalador do Android ---------- */
    private static final String APK_URL = "https://github.com/brunogustavo7exe-max/rivo-app/releases/download/apk-latest/rivo.apk";
    private volatile boolean baixando = false;

    private boolean podeInstalarApk() {
        return Build.VERSION.SDK_INT < 26 || getContext().getPackageManager().canRequestPackageInstalls();
    }

    private void avisarUpd(String st, int pct, String msg) {
        JSObject o = new JSObject();
        o.put("st", st);
        o.put("pct", pct);
        o.put("msg", msg);
        notifyListeners("upd", o);
    }

    @PluginMethod
    public void podeInstalar(PluginCall c) {
        JSObject o = new JSObject();
        o.put("ok", podeInstalarApk());
        c.resolve(o);
    }

    /** abre a tela "Instalar apps desconhecidos" do RIVO (o Android exige que a pessoa ligue isso uma vez) */
    @PluginMethod
    public void pedirInstalar(PluginCall c) {
        Context ctx = getContext();
        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); c.resolve(); } catch (Exception e) { c.reject("não consegui abrir a permissão"); }
    }

    @PluginMethod
    public void baixarEInstalar(PluginCall c) {
        if (!podeInstalarApk()) { c.reject("sem permissão para instalar"); return; }
        if (baixando) { c.resolve(); return; }
        baixando = true;
        c.resolve();
        final Context ctx = getContext();
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                File dir = new File(ctx.getCacheDir(), "update");
                if (!dir.exists()) dir.mkdirs();
                final File f = new File(dir, "rivo.apk");
                if (f.exists()) f.delete();
                con = (HttpURLConnection) new URL(APK_URL).openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(30000);
                con.setInstanceFollowRedirects(true);
                if (con.getResponseCode() != 200) throw new IOException("HTTP " + con.getResponseCode());
                int total = con.getContentLength();
                InputStream in = con.getInputStream();
                FileOutputStream out = new FileOutputStream(f);
                byte[] buf = new byte[32768];
                long lido = 0;
                int ultimo = -1, n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    lido += n;
                    if (total > 0) {
                        int pct = (int) (lido * 100 / total);
                        if (pct != ultimo) { ultimo = pct; avisarUpd("baixando", pct, ""); }
                    }
                }
                out.close();
                in.close();
                if (f.length() < 1000000L) throw new IOException("arquivo incompleto");
                avisarUpd("pronto", 100, "");
                h.post(() -> {
                    try {
                        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(uri, "application/vnd.android.package-archive");
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(i);
                    } catch (Exception e) {
                        avisarUpd("erro", 0, "Não consegui abrir o instalador.");
                    }
                });
            } catch (Exception e) {
                avisarUpd("erro", 0, "Não consegui baixar a atualização. Confira a internet e tente de novo.");
            } finally {
                baixando = false;
                if (con != null) con.disconnect();
            }
        }).start();
    }
}
