package com.cocolorussococo.flyer;


import android.content.res.Configuration;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class DownloadActivity extends AppCompatActivity {

    ArrayList<MulticastSocket> sockets;
    Timer beaconTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);

        // Initialize device icon
        boolean isTablet = (getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
        ImageView icon = findViewById(R.id.deviceType);
        icon.setImageResource(isTablet ? R.drawable.outline_tablet_24 : R.drawable.outline_smartphone_24);
        // Initialize device name
        TextView deviceName = findViewById(R.id.deviceName);
        deviceName.setText(Host.getHostname(this));

        ImageView anim = findViewById(R.id.animation);
        AnimatedVectorDrawable drawable = (AnimatedVectorDrawable) anim.getDrawable();
        drawable.start();

        startBeacon();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (beaconTimer != null) beaconTimer.cancel();
        // Dispose of the socket
        if (sockets != null) {
            for (MulticastSocket socket : sockets) {
                socket.close();
            }
        }
    }

    private void startBeacon() {
        // Create UDP station
        try {
            ArrayList<NetworkInterface> interfaces = Host.getActiveInterfaces();
            sockets = new ArrayList<>(interfaces.size());
            for (NetworkInterface networkInterface : interfaces) {
                MulticastSocket socket = new MulticastSocket();
                socket.setNetworkInterface(networkInterface);
                socket.setTimeToLive(255);
                sockets.add(socket);
            }

            byte[] send = new byte[132];
            String hostname = Host.getHostname(DownloadActivity.this);
            // Send TCP port number
            int portNo = 55555;
            send[0] = (byte) ((portNo >>> 8) & 255);
            send[1] = (byte) (portNo & 255);
            // Set device type (0 = Phone, 1 = Tablet)
            send[2] = (byte) (((getResources().getConfiguration().screenLayout
                                    & Configuration.SCREENLAYOUT_SIZE_MASK)
                                    >= Configuration.SCREENLAYOUT_SIZE_LARGE) ? 1 : 0);
            byte[] name = hostname.substring(0, Math.min(64, hostname.length())).getBytes();
            System.arraycopy(name, 0, send, 3, name.length);

            InetSocketAddress group = new InetSocketAddress(InetAddress.getByName("239.255.255.250"), 10468);
            DatagramPacket packet = new DatagramPacket(send, send.length, group);

            beaconTimer = new Timer();
            beaconTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        for (MulticastSocket socket : sockets)
                            socket.send(packet);
                    } catch (IOException ignored) {}
                }
            }, 0, 2000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}