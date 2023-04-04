package com.cocolorussococo.flyer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

public class NetworkUpdateCallback {

    BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkInfo info = (NetworkInfo) intent.getExtras().get("networkInfo");
            if (info.getState().toString().equals("CONNECTED"))
                onInterfaceAvailable.run();
        }
    };

    BroadcastReceiver apReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!"android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) return;

            int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
            if (state % 10 == WifiManager.WIFI_STATE_ENABLED)
                onInterfaceAvailable.run();
        }
    };
    Activity context;
    Runnable onInterfaceAvailable;

    public NetworkUpdateCallback(Activity context) {
        this.context = context;
    }
    public void register(Runnable toRun) {
        if (onInterfaceAvailable != null) return;

        onInterfaceAvailable = toRun;

        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        context.registerReceiver(wifiReceiver, f);

        f = new IntentFilter();
        f.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");

        context.registerReceiver(apReceiver, f);
    }
    public void unregister() {
        context.unregisterReceiver(wifiReceiver);
        context.unregisterReceiver(apReceiver);

        onInterfaceAvailable = null;
    }
}
