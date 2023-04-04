package com.cocolorussococo.flyer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class ReceiverTest extends AppCompatActivity {

    BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkInfo info = (NetworkInfo) intent.getExtras().get("networkInfo");
            if (info.getState().toString().equals("CONNECTED"))
                registerSockets();
        }
    };

    BroadcastReceiver apReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!"android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) return;

            int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
            if (state % 10 == WifiManager.WIFI_STATE_ENABLED)
                registerSockets();
        }
    };

    private void registerSockets() {
        Toast.makeText(ReceiverTest.this, "Connesso", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver_test);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (isChangingConfigurations()) return;

        unregisterReceiver(wifiReceiver);
        unregisterReceiver(apReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isChangingConfigurations()) return;

        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        registerReceiver(wifiReceiver, f);

        f = new IntentFilter();
        f.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");

        registerReceiver(apReceiver, f);
    }
}