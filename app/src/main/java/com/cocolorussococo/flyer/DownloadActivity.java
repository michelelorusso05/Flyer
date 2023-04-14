package com.cocolorussococo.flyer;


import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.cocolorussococo.flyer.Host.DeviceTypes;
import com.cocolorussococo.flyer.Host.PacketTypes;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadActivity extends AppCompatActivity {

    final ArrayList<MulticastSocket> sockets = new ArrayList<>();
    Timer beaconTimer;
    ServerSocket serverSocket;
    NetworkUpdateCallback callback;
    static volatile Socket socket;
    static AtomicInteger currentPort = new AtomicInteger(0);
    static InetSocketAddress group;

    SnackbarBroadcastManager snackbarDispatcher = new SnackbarBroadcastManager();

    static {
        try {
            group = new InetSocketAddress(InetAddress.getByName("224.0.0.255"), 10468);
        } catch (UnknownHostException ignored) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);

        // Check permission failsafe
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                !(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED))
            finish();

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
        final WorkManager wm = WorkManager.getInstance(DownloadActivity.this);
        String id = socket.getInetAddress().getHostAddress() + socket.getPort();
        UUID uuid = UUID.randomUUID();
        snackbarDispatcher.enqueue(uuid);

        OneTimeWorkRequest downloadWorkRequest = new OneTimeWorkRequest.Builder(FileDownloadWorker.class)
                .setId(uuid)
                .addTag(String.valueOf(currentPort.get()))
                .build();

        wm.enqueueUniqueWork(String.valueOf(id.hashCode()), ExistingWorkPolicy.KEEP, downloadWorkRequest);

        runOnUiThread(() -> {
            Snackbar s = Snackbar.make(findViewById(R.id.coordinatorLayout), "", Snackbar.LENGTH_INDEFINITE);
            FileOperationPreview operationPreview = new FileOperationPreview(this, s, FileOperationPreview.Mode.RECEIVER);

            LiveData<WorkInfo> workInfoLiveData = wm.getWorkInfoByIdLiveData(uuid);

            workInfoLiveData.observe(DownloadActivity.this, workInfo -> {
                boolean hasFinished = workInfo.getState().isFinished();

                Data data = hasFinished ? workInfo.getOutputData() : workInfo.getProgress();

                String filename = data.getString("filename");
                String mimeType = data.getString("mimeType");
                String transmitter = data.getString("transmitterName");

                // Empty callbacks are to be ignored
                if (!data.getKeyValueMap().isEmpty()) {
                    if (!operationPreview.getSet()) {
                        operationPreview.setInfo(filename, mimeType, transmitter);
                    }

                    int progress = data.getInt("percentage", 0);

                    operationPreview.updateProgress(progress);

                    if (!s.isShown() && snackbarDispatcher.canShow(uuid))
                        s.show();
                }

                // Cancelled operations have an empty callback, so handle it here
                if (hasFinished) {
                    WorkInfo.State state = workInfo.getState();

                    if (state == WorkInfo.State.SUCCEEDED) {
                        operationPreview.setSucceeded(R.string.touch_to_open);

                        String savedFileURI = data.getString("fileURI");
                        Uri uri = Uri.parse(savedFileURI);

                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        intent.setDataAndType(uri, mimeType);

                        operationPreview.setOnClick(v -> {
                            s.dismiss();
                            try {
                                startActivity(intent);
                            } catch (ActivityNotFoundException e) {
                                Snackbar.make(findViewById(R.id.coordinatorLayout), "Non ci sono app per questo tipo di contenuto", Snackbar.LENGTH_SHORT).show();
                            }
                        });
                    }
                    else if (state == WorkInfo.State.FAILED || state == WorkInfo.State.CANCELLED) {
                        operationPreview.setFailed();
                    }
                }
            });

            s.addCallback(new Snackbar.Callback() {
                @Override
                public void onDismissed(Snackbar transientBottomBar, int event) {
                    super.onDismissed(transientBottomBar, event);

                    snackbarDispatcher.yield(uuid);
                    workInfoLiveData.removeObservers(DownloadActivity.this);
                }
            });
        });
    }
    private synchronized void sendMessage(DatagramPacket packet) throws IOException {
        synchronized (sockets) {
            for (MulticastSocket multicastSocket : sockets)
                multicastSocket.send(packet);
        }
    }
    private synchronized void updateInterfaces() throws IOException {
        clearInterfaces();
        synchronized (sockets) {

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
    private synchronized void clearInterfaces() {
        synchronized (sockets) {
            for (MulticastSocket socket : sockets) {
                socket.close();
            }

            sockets.clear();
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
                        sendMessage(packet);

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