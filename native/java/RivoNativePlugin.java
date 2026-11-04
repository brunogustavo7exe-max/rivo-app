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
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
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
}
