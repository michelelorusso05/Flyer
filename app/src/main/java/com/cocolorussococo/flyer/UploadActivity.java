package com.cocolorussococo.flyer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.UUID;

public class UploadActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    FoundDevicesAdapter adapter;
    TextView selectedFile;
    MulticastSocket udpSocket;
    WifiManager.MulticastLock multicastLock;
    Uri selectedUri;
    static boolean open = false;
    static InetSocketAddress group;

    static {
        try {
            group = new InetSocketAddress(InetAddress.getByName("224.0.0.255"), 10468);
        } catch (UnknownHostException ignored) {}
    }
    NetworkUpdateCallback callback = new NetworkUpdateCallback(this);
    final ArrayList<NetworkInterface> subbedInterfaces = new ArrayList<>();
    SnackbarBroadcastManager snackbarDispatcher = new SnackbarBroadcastManager();

    ActivityResultLauncher<String> mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) {
                    finish();
                    open = false;
                    return;
                }
                selectedUri = uri;
                postOpenFile();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        multicastLock = wm.createMulticastLock("multicast");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        // Check permission failsafe
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                !(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED))
            finish();

        recyclerView = findViewById(R.id.foundDevices);
        adapter = new FoundDevicesAdapter(this, this::connect);
        recyclerView.setAdapter(adapter);
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        recyclerView.setLayoutManager(new GridLayoutManager(this, landscape ? 6 : 3));

        selectedFile = findViewById(R.id.selectedFile);

        // Get text from share intent (not supported yet)
        Uri shareIntentUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        if (getIntent().getStringExtra(Intent.EXTRA_TEXT) != null && shareIntentUri == null) {
            Toast.makeText(this, "Flyer non permette l'invio di testo semplice al momento.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get file from share intent
        if (shareIntentUri != null && selectedUri == null) {
            selectedUri = shareIntentUri;
            postOpenFile();
            return;
        }

        // If activity gets recreated while FileChooser is launched, do not launch it again
        if (open) return;

        // Get URI back from saved instance state
        String uri = (savedInstanceState != null) ? savedInstanceState.getString("selectedUri") : null;
        if (uri != null) {
            selectedUri = Uri.parse(uri);
            postOpenFile();
        }
        else
            openFile();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (udpSocket == null || isChangingConfigurations()) return;
        Log.d("Paused", "paused");
        try {
            unsubAll();
        } catch (IOException ignored) {}
        adapter.forgetDevices();
        adapter.cleanup();

        callback.unregister();
        udpSocket.close();
        udpSocket = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (selectedUri == null || isChangingConfigurations()) return;
        Log.d("Resumed", "resumed");
        searchForDevices();
        adapter.restart();

        callback.register(this::updateInterfaces);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedUri != null) outState.putString("selectedUri", selectedUri.toString());
    }

    private void searchForDevices() {
        adapter.forgetDevices();
        try {
            udpSocket = new MulticastSocket(10468);
        } catch (IOException ignored) {}
        new Thread(this::listenUDP).start();
    }
    private void unsubAll() throws IOException {
        synchronized (subbedInterfaces) {
            for (NetworkInterface networkInterface : subbedInterfaces)
                udpSocket.leaveGroup(group, networkInterface);

            subbedInterfaces.clear();
        }
    }
    private void updateInterfaces() {
        try {
            unsubAll();

            synchronized (subbedInterfaces) {
                for (NetworkInterface networkInterface : Host.getActiveInterfaces()) {
                    udpSocket.joinGroup(group, networkInterface);
                    subbedInterfaces.add(networkInterface);
                }
            }
        } catch (IOException ignored) {}
    }
    private void listenUDP() {
        updateInterfaces();

        DatagramPacket received = new DatagramPacket(new byte[132], 132);

        while (true) {
            try {
                udpSocket.receive(received);
                Host host = PacketUtils.deencapsulate(received);

                if (host.getPacketType() == Host.PacketTypes.FORGETME) {
                    runOnUiThread(() -> adapter.forgetDevice(host));
                    continue;
                }
                runOnUiThread(() -> adapter.addDevice(host));

            } catch (SocketException e) {
                Log.w("Socket destroyed", "Discovery was cancelled");
                break;
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private void openFile() {
        open = true;
        mGetContent.launch("*/*");
    }
    private void postOpenFile() {
        open = false;

        selectedFile.setText(FileMappings.getFilenameFromURI(UploadActivity.this, selectedUri)
                /*+ " " + getContentResolver().getType(selectedUri)*/);
        selectedFile.setCompoundDrawablesRelativeWithIntrinsicBounds(FileMappings.getIconFromUri(UploadActivity.this, selectedUri), null, null, null);
    }

    public void connect(@NonNull Host host) {
        WorkManager wm = WorkManager.getInstance(UploadActivity.this);

        Data.Builder workerData = new Data.Builder();
        workerData.putString("targetHost", host.getIp().getHostAddress());
        workerData.putInt("port", host.getPort());
        workerData.putString("file", selectedUri.toString());

        String uniqueWorkID = selectedUri.toString().concat(host.getIp().toString());

        UUID uuid = UUID.randomUUID();
        snackbarDispatcher.enqueue(uuid);

        OneTimeWorkRequest downloadWorkRequest = new OneTimeWorkRequest.Builder(FileUploadWorker.class)
                .setId(uuid)
                .addTag(uniqueWorkID)
                .setInputData(workerData.build())
                .build();

        wm.enqueueUniqueWork(uniqueWorkID, ExistingWorkPolicy.KEEP, downloadWorkRequest);

        runOnUiThread(() -> {
            Snackbar s = Snackbar.make(findViewById(R.id.coordinatorLayout), "", Snackbar.LENGTH_INDEFINITE);
            FileOperationPreview operationPreview = new FileOperationPreview(this, s, FileOperationPreview.Mode.SENDER);

            LiveData<WorkInfo> workInfoLiveData = wm.getWorkInfoByIdLiveData(uuid);

            workInfoLiveData.observe(UploadActivity.this, workInfo -> {
                boolean hasFinished = workInfo.getState().isFinished();

                Data data = hasFinished ? workInfo.getOutputData() : workInfo.getProgress();

                String filename = (String) selectedFile.getText();
                String mimeType = getContentResolver().getType(selectedUri);
                String receiver = host.getName();

                // Empty callbacks are to be ignored
                if (!data.getKeyValueMap().isEmpty()) {
                    if (!operationPreview.getSet()) {
                        operationPreview.setInfo(filename, mimeType, receiver);
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
                        operationPreview.setSucceeded(R.string.upload_complete);
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
                    workInfoLiveData.removeObservers(UploadActivity.this);
                }
            });
        });


    }
}