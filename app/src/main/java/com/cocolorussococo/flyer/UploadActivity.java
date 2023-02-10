package com.cocolorussococo.flyer;

import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class UploadActivity extends AppCompatActivity {
    ActivityResultLauncher<String> mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) return;
                new Thread(() -> connect(uri)).start();
            });


    RecyclerView recyclerView;
    FoundDevicesAdapter adapter;
    TextView status;
    TextView hotspotWarning;
    View pBar;
    ImageButton retryButton;
    static DatagramSocket udpSocket;
    static Timer timer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

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

        searchForDevices();
        retryButton.setOnClickListener((View v) -> searchForDevices());

        SwipeRefreshLayout refreshLayout = findViewById(R.id.swipeRefresh);
        refreshLayout.setOnRefreshListener(() -> {
            searchForDevices();
            refreshLayout.setRefreshing(false);
        });
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
                Log.d("AAAAAAAAA", String.valueOf(udpSocket.isClosed()));
            }
            udpSocket = new DatagramSocket(10469);
            udpSocket.setSoTimeout(0);
            byte[] send = new byte[132];
            send[1] = 104;
            Pair<InetAddress, Boolean> addr = Host.getBroadcastEx();
            runOnUiThread(() -> hotspotWarning.setVisibility(addr.second ? View.VISIBLE : View.GONE));
            DatagramPacket packet = new DatagramPacket(send, send.length, Inet4Address.getByName("192.168.1.5"), 10468);
            udpSocket.setBroadcast(true);
            udpSocket.send(packet);

            if (timer != null) {
                timer.cancel();
            }
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    socketTimeout();
                }
            }, 2000);

            DatagramPacket received = new DatagramPacket(new byte[132], 132);

            while (true) {
                try {
                    udpSocket.receive(received);
                    Log.d("Packet", Arrays.toString(received.getData()));
                    if (received.getData()[1] == 104) {
                        // Scarta il paccetto se viene da un client
                        continue;
                    }
                    byte[] data = received.getData();
                    String name = new String(data, 3, 128);
                    int port = Byte.toUnsignedInt(data[1]) + (Byte.toUnsignedInt(data[0]) << 8);
                    Host host = new Host(packet.getAddress(), name, port, data[2]);
                    if (!adapter.hasAlreadyBeenDiscovered(host)) {
                        runOnUiThread(() -> {
                            adapter.addDevice(host);
                        });
                    }
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
        if (udpSocket != null)
            udpSocket.close();
        runOnUiThread(() -> {
            status.setText(getResources().getQuantityString(R.plurals.number_of_devices, adapter.getItemCount(), adapter.getItemCount() ));
            pBar.setVisibility(View.GONE);
            retryButton.setVisibility(View.VISIBLE);
        });
    }
    private void openFile() {
        mGetContent.launch("*/*");
    }

    private void connect(Uri toSend) {
        WorkManager wm = WorkManager.getInstance(UploadActivity.this);

        Data.Builder data = new Data.Builder();
        // data.putString("targetHost", ipInput.getText().toString());
        data.putString("file", toSend.toString());

        OneTimeWorkRequest downloadWorkRequest = new OneTimeWorkRequest.Builder(FileUploadWorker.class)
                .addTag(toSend.toString())
                .setInputData(data.build())
                .build();

        wm.enqueueUniqueWork(toSend.toString(), ExistingWorkPolicy.KEEP, downloadWorkRequest);
    }
}