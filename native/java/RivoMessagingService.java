package com.dcastrolabs.rivo;

import android.content.Intent;
import androidx.core.content.ContextCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class RivoMessagingService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage m) {
        Map<String, String> d = m.getData();
        if (d == null || !"entrega".equals(d.get("tipo"))) return;
        // app aberto na frente: o próprio app mostra a chamada
        if (RivoState.fg && RivoNativePlugin.instancia != null) {
            RivoNativePlugin.instancia.emitir(d);
            return;
        }
        Intent i = new Intent(this, AlertService.class);
        for (Map.Entry<String, String> e : d.entrySet()) i.putExtra(e.getKey(), e.getValue());
        try { ContextCompat.startForegroundService(this, i); } catch (Exception e) { /* sistema recusou */ }
    }

    @Override
    public void onNewToken(String token) {
        getSharedPreferences("rivo", MODE_PRIVATE).edit().putString("fcm", token).apply();
    }
}
