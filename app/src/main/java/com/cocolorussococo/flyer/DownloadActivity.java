package com.cocolorussococo.flyer;


import android.content.res.Configuration;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadActivity extends AppCompatActivity {

    ArrayList<MulticastSocket> sockets;
    Timer beaconTimer;
    ServerSocket serverSocket;
    static Socket socket;
    static AtomicInteger currentPort = new AtomicInteger(0);

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

        initSocket();
        startBeacon();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isChangingConfigurations()) return;

        if (beaconTimer != null) beaconTimer.cancel();
        // Dispose of the socket
        if (sockets != null) {
            for (MulticastSocket socket : sockets) {
                socket.close();
            }
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        startBeacon();
        initSocket();
    }

    private void initSocket() {
        try {
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
    public static Socket consumeSocket() {
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
    }

    private void startBeacon() {
        // Create UDP station
        try {
            ArrayList<NetworkInterface> interfaces = Host.getActiveInterfaces();
            sockets = new ArrayList<>(interfaces.size());
            for (NetworkInterface networkInterface : interfaces) {
                MulticastSocket socket = new MulticastSocket();
                socket.setNetworkInterface(networkInterface);
                socket.setTimeToLive(1);
                sockets.add(socket);
            }

            byte[] send = new byte[132];
            String hostname = Host.getHostname(DownloadActivity.this);
            // Send TCP port number

            // Set device type (0 = Phone, 1 = Tablet)
            send[2] = (byte) (((getResources().getConfiguration().screenLayout
                                    & Configuration.SCREENLAYOUT_SIZE_MASK)
                                    >= Configuration.SCREENLAYOUT_SIZE_LARGE) ? 1 : 0);
            byte[] name = hostname.substring(0, Math.min(64, hostname.length())).getBytes();
            System.arraycopy(name, 0, send, 3, name.length);

            InetSocketAddress group = new InetSocketAddress(InetAddress.getByName("239.255.255.250"), 10468);

            beaconTimer = new Timer();
            beaconTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        int portNum = currentPort.get();
                        send[0] = (byte) ((portNum >>> 8) & 255);
                        send[1] = (byte) (portNum & 255);

                        DatagramPacket packet = new DatagramPacket(send, send.length, group);

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