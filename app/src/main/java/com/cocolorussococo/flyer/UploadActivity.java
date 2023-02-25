package com.cocolorussococo.flyer;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class UploadActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    FoundDevicesAdapter adapter;
    TextView status;
    TextView hotspotWarning;
    TextView selectedFile;
    View pBar;
    ImageButton retryButton;
    MulticastSocket udpSocket;
    WifiManager.MulticastLock multicastLock;
    static Timer timer;
    Uri selectedUri;
    static boolean open = false;

    ActivityResultLauncher<String> mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
            uri -> {
                open = false;
                if (uri == null) {
                    finish();
                    return;
                }
                selectedUri = uri;
                postOpenFile();

                searchForDevices();
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
        adapter = new FoundDevicesAdapter(this);
        recyclerView.setAdapter(adapter);
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        recyclerView.setLayoutManager(new GridLayoutManager(this, landscape ? 6 : 3));

        status = findViewById(R.id.status);
        pBar = findViewById(R.id.progressBar);
        retryButton = findViewById(R.id.retryButton);
        hotspotWarning = findViewById(R.id.hotspotWarning);
        hotspotWarning.setText(HtmlCompat.fromHtml(getString(R.string.multiple_connections_warning), HtmlCompat.FROM_HTML_MODE_LEGACY));
        selectedFile = findViewById(R.id.selectedFile);

        //searchForDevices();
        retryButton.setOnClickListener((View v) -> searchForDevices());

        SwipeRefreshLayout refreshLayout = findViewById(R.id.swipeRefresh);
        refreshLayout.setOnRefreshListener(() -> {
            searchForDevices();
            refreshLayout.setRefreshing(false);
        });

        if (open) return;

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
        if (udpSocket != null)
            udpSocket.close();
    }

    @Override
    protected void onResume() {
        super.onResume();

        new Thread(this::searchForDevices).start();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedUri != null) outState.putString("selectedUri", selectedUri.toString());
    }

    private void searchForDevices() {
        adapter.forgetDevices();

        status.setText(R.string.searching_label);
        pBar.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        new Thread(this::listenUDP).start();
    }
    private void listenUDP() {
        Log.d("UDP", "started");
        try {

            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.close();
                Thread.sleep(1000);
            }

            if (timer != null) {
                timer.cancel();
            }

            udpSocket = new MulticastSocket(10468);
            InetSocketAddress group = new InetSocketAddress(InetAddress.getByName("239.255.255.250"), 10468);

            for(NetworkInterface networkInterface : Host.getActiveInterfaces())
                udpSocket.joinGroup(group, networkInterface);

            DatagramPacket received = new DatagramPacket(new byte[132], 132);

            while (true) {
                try {
                    udpSocket.receive(received);

                    byte[] data = received.getData();
                    String name = new String(data, 3, 128);
                    int port = Byte.toUnsignedInt(data[1]) + (Byte.toUnsignedInt(data[0]) << 8);
                    Host host = new Host(received.getAddress(), name, port, data[2]);
                    runOnUiThread(() -> adapter.addDevice(host));

                } catch (SocketException e) {
                    Log.w("Socket destroyed", "Discovery was cancelled");
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void socketTimeout() {
        // multicastLock.release();
        if (udpSocket != null)
            udpSocket.close();
        runOnUiThread(() -> {
            status.setText(getResources().getQuantityString(R.plurals.number_of_devices, adapter.getItemCount(), adapter.getItemCount() ));
            pBar.setVisibility(View.GONE);
            retryButton.setVisibility(View.VISIBLE);
        });
    }
    private void openFile() {
        open = true;
        mGetContent.launch("*/*");
    }
    private void postOpenFile() {
        selectedFile.setText(FileMappings.getFilenameFromURI(UploadActivity.this, selectedUri) + " "
                + getContentResolver().getType(selectedUri));
        selectedFile.setCompoundDrawablesRelativeWithIntrinsicBounds(FileMappings.getIconFromUri(UploadActivity.this, selectedUri), null, null, null);
        adapter.setFileToSend(selectedUri);
    }

    public static void connect(Context ctx, Uri toSend, InetAddress address, int port) {
        WorkManager wm = WorkManager.getInstance(ctx);

        Data.Builder data = new Data.Builder();
        data.putString("targetHost", address.getHostAddress());
        data.putInt("port", port);
        data.putString("file", toSend.toString());

        OneTimeWorkRequest downloadWorkRequest = new OneTimeWorkRequest.Builder(FileUploadWorker.class)
                .addTag(toSend.toString())
                .setInputData(data.build())
                .build();

        wm.enqueueUniqueWork(toSend.toString(), ExistingWorkPolicy.KEEP, downloadWorkRequest);
    }
}