package com.cocolorussococo.flyer;


import android.content.res.Configuration;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.cocolorussococo.flyer.Host.*;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadActivity extends AppCompatActivity {

    final ArrayList<MulticastSocket> sockets = new ArrayList<>();
    Timer beaconTimer;
    ServerSocket serverSocket;
    NetworkUpdateCallback callback;
    static volatile Socket socket;
    static AtomicInteger currentPort = new AtomicInteger(0);
    static InetSocketAddress group;

    static {
        try {
            group = new InetSocketAddress(InetAddress.getByName("224.0.0.255"), 10468);
        } catch (UnknownHostException ignored) {}
    }

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

        if (serverSocket == null || serverSocket.isClosed()) {
            startBeacon();
            initSocket();
        }

        callback = new NetworkUpdateCallback(this);

        callback.register(() -> {
            try {
                updateInterfaces();
            } catch (IOException ignored) {}
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isChangingConfigurations()) return;
        Log.d("Socket", "Shutting down");

        if (beaconTimer != null) beaconTimer.cancel();

        // Dispose of the socket
        if (sockets != null) {
            byte[] packet = PacketUtils.encapsulate(
                    currentPort.get(),
                    // Ignored
                    DeviceTypes.PHONE,
                    PacketTypes.FORGETME,
                    // Ignored
                    Host.getHostname(DownloadActivity.this)
            );
            final DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, group);

            new Thread(() -> {
                try {
                    sendMessage(datagramPacket);
                } catch (IOException ignored) {}
            }).start();
        }
        try {
            serverSocket.close();
            serverSocket = null;
        } catch (IOException ignored) {}

        if (callback != null)
            callback.unregister();

        callback = null;
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        if (isChangingConfigurations()) return;

        startBeacon();
        initSocket();

        if (callback == null) {
            callback = new NetworkUpdateCallback(this);
            callback.register(() -> {
                try {
                    updateInterfaces();
                } catch (IOException ignored) {}
            });
        }
    }

    private void initSocket() {
        try {
            int p = currentPort.get();
            if (p != 0) {
                try {
                    serverSocket = new ServerSocket(p);
                } catch (IOException e) {
                    Log.d("Socket recreation", "Port previously bounded is no longer available, getting new port...");
                    serverSocket = new ServerSocket(0);
                }
            }
            else
                serverSocket = new ServerSocket(0);
            currentPort.set(serverSocket.getLocalPort());

            new Thread(() -> {
                try {
                    while (true) {
                        socket = serverSocket.accept();
                        onConnectionReceived();
                    }
                } catch (IOException ignored) {}
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public synchronized static Socket consumeSocket() {
        Socket s = socket;
        socket = null;
        return s;
    }
    private void onConnectionReceived() {
        WorkManager wm = WorkManager.getInstance(DownloadActivity.this);

        OneTimeWorkRequest downloadWorkRequest = new OneTimeWorkRequest.Builder(FileDownloadWorker.class)
                .addTag(String.valueOf(currentPort.get()))
                .build();

        wm.enqueue(downloadWorkRequest);

        Snackbar.make(findViewById(R.id.coordinatorLayout), R.string.receiving_file, Snackbar.LENGTH_SHORT).show();
    }
    private synchronized void sendMessage(DatagramPacket packet) throws IOException {
        synchronized (sockets) {
            for (MulticastSocket multicastSocket : sockets)
                multicastSocket.send(packet);
        }
    }
    private synchronized void updateInterfaces() throws IOException {
        Log.d("Update", "Interfaces refreshed");
        synchronized (sockets) {
            sockets.clear();

            ArrayList<NetworkInterface> interfaces = Host.getActiveInterfaces();
            for (NetworkInterface networkInterface : interfaces) {
                MulticastSocket socket = new MulticastSocket();
                socket.setNetworkInterface(networkInterface);
                // Don't leave the local network
                socket.setTimeToLive(1);
                // Reliability
                socket.setTrafficClass(0x04);
                sockets.add(socket);
            }
        }
    }
    private void startBeacon() {
        Log.d("Socket", "Started");
        // Create UDP station
        try {
            updateInterfaces();

            byte[] send = PacketUtils.encapsulate(
                    currentPort.get(),
                    ((getResources().getConfiguration().screenLayout
                            & Configuration.SCREENLAYOUT_SIZE_MASK)
                            >= Configuration.SCREENLAYOUT_SIZE_LARGE) ? DeviceTypes.TABLET : DeviceTypes.PHONE,
                    PacketTypes.OFFER,
                    Host.getHostname(DownloadActivity.this));

            beaconTimer = new Timer();
            beaconTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        PacketUtils.updatePort(send, currentPort.get());

                        DatagramPacket packet = new DatagramPacket(send, send.length, group);

                        for (MulticastSocket socket : sockets)
                            socket.send(packet);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, 0, 1000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}