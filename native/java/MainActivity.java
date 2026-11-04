package com.dcastrolabs.rivo;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RivoNativePlugin.class); // precisa vir antes do super.onCreate
        super.onCreate(savedInstanceState);
        chegou(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        chegou(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        RivoState.fg = true;
        try { stopService(new Intent(this, AlertService.class)); } catch (Exception e) { /* nada a parar */ }
    }

    @Override
    public void onPause() {
        RivoState.fg = false;
        super.onPause();
    }

    /** Abriu por causa de uma chamada: acende a tela e aparece por cima da tela de bloqueio. */
    private void chegou(Intent i) {
        if (i == null || !i.getBooleanExtra("rivoAlerta", false)) return;
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }
}
