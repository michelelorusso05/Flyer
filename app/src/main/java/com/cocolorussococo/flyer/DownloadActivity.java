package com.cocolorussococo.flyer;


import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.cocolorussococo.flyer.Host.DeviceTypes;
import com.cocolorussococo.flyer.Host.PacketTypes;
import com.google.android.material.progressindicator.CircularProgressIndicator;
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

    // Last started worker, which is the one authorized to post updates on the main UI
    UUID broadcastingBackgroundWorker;

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
        final WorkManager wm = WorkManager.getInstance(DownloadActivity.this);
        String id = socket.getInetAddress().getHostAddress() + socket.getPort();
        UUID uuid = UUID.randomUUID();

        broadcastingBackgroundWorker = uuid;

        OneTimeWorkRequest downloadWorkRequest = new OneTimeWorkRequest.Builder(FileDownloadWorker.class)
                .setId(uuid)
                .addTag(String.valueOf(currentPort.get()))
                .build();

        wm.enqueueUniqueWork(String.valueOf(id.hashCode()), ExistingWorkPolicy.KEEP, downloadWorkRequest);

        runOnUiThread(() -> {
            Snackbar s = Snackbar.make(findViewById(R.id.coordinatorLayout), "", Snackbar.LENGTH_INDEFINITE);
            @SuppressLint("InflateParams") View customView = getLayoutInflater().inflate(R.layout.operation_preview, null);
            s.getView().setBackgroundColor(Color.TRANSPARENT);

            Snackbar.SnackbarLayout snackbarLayout = (Snackbar.SnackbarLayout) s.getView();
            snackbarLayout.setPadding(0, 0, 0, 0);

            TextView filenameView = customView.findViewById(R.id.filenameView);
            TextView statusView = customView.findViewById(R.id.statusView);
            ImageView iconView = customView.findViewById(R.id.statusIcon);
            ImageView fileIcon = customView.findViewById(R.id.fileIcon);

            CircularProgressIndicator progressBar = customView.findViewById(R.id.transferProgress);
            progressBar.setInterpolator(new DecelerateInterpolator());

            snackbarLayout.addView(customView, 0);

            LiveData<WorkInfo> workInfoLiveData = wm.getWorkInfoByIdLiveData(uuid);

            workInfoLiveData.observe(DownloadActivity.this, workInfo -> {
                Data data = workInfo.getProgress();

                boolean hasFinished = workInfo.getState().isFinished();

                if (hasFinished) {
                    data = workInfo.getOutputData();
                    System.out.println(data.getKeyValueMap());
                }

                String filename = data.getString("filename");
                String mimeType = data.getString("mimeType");
                if (filename != null) {
                    filenameView.setText(filename);
                    statusView.setText(R.string.receiving_file);

                    fileIcon.setImageDrawable(FileMappings.getIconFromMimeType(getApplicationContext(), mimeType));

                    int progress = data.getInt("percentage", 0);

                    progressBar.setIndeterminate(false);
                    progressBar.setMax(100);
                    progressBar.setProgressCompat(progress, true);

                    if (hasFinished) {
                        boolean succeded = data.getBoolean("succeded", true);

                        if (succeded) {
                            progressBar.setProgressCompat(100, true);
                            progressBar.setIndicatorColor(0xFF45CF40);

                            iconView.setImageResource(R.drawable.round_check_24);
                            iconView.setColorFilter(0xFF45CF40);
                            statusView.setText(R.string.touch_to_open);

                            String savedFileURI = data.getString("fileURI");
                            Uri uri = Uri.parse(savedFileURI);

                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.setDataAndType(uri, mimeType);

                            View clickableBackground = customView.findViewById(R.id.clickableView);

                            clickableBackground.setClickable(true);
                            clickableBackground.setFocusable(true);
                            clickableBackground.setOnClickListener(v -> {
                                s.dismiss();
                                try {
                                    startActivity(intent);
                                } catch (ActivityNotFoundException e) {
                                    Snackbar.make(findViewById(R.id.coordinatorLayout), "Non ci sono app per questo tipo di contenuto", Snackbar.LENGTH_SHORT).show();
                                }
                            });
                        }
                        else {
                            progressBar.setIndicatorColor(0xFFC92647);

                            iconView.setImageResource(R.drawable.round_error_24);
                            iconView.setColorFilter(0xFFC92647);
                            statusView.setText(R.string.transfer_cancelled);
                        }

                        /*
                        new CountDownTimer(3000, 3000) {
                            @Override
                            public void onTick(long millisUntilFinished) {}
                            @Override
                            public void onFinish() {
                                runOnUiThread(s::dismiss);
                            }
                        }.start();
                         */
                    }

                    if (!s.isShown() && broadcastingBackgroundWorker == uuid)
                        s.show();
                }
            });

            s.addCallback(new Snackbar.Callback() {
                @Override
                public void onDismissed(Snackbar transientBottomBar, int event) {
                    super.onDismissed(transientBottomBar, event);

                    if (broadcastingBackgroundWorker == uuid) {
                        broadcastingBackgroundWorker = null;
                    }
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