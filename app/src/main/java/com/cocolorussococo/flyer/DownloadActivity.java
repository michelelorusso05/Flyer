package com.cocolorussococo.flyer;

import static com.cocolorussococo.flyer.Host.getBroadcast;
import static com.cocolorussococo.flyer.Host.getHostname;

import android.content.res.Configuration;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;

public class DownloadActivity extends AppCompatActivity {

    DatagramSocket datagramSocket;

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

        acceptUDP();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Dispose of the socket
        if (datagramSocket != null)
            datagramSocket.close();
    }

    private void acceptUDP() {
        Thread udpStation = new Thread(() -> {
            // Create UDP station
            try {
                datagramSocket = new DatagramSocket(10468);
                InetAddress broadcast = Host.getBroadcast();
                System.out.println(broadcast);
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

                DatagramPacket rec = new DatagramPacket(new byte[132], 132);
                while (true) {
                    datagramSocket.receive(rec);
                    System.out.println("Ricevuto: " + rec.getAddress());
                    DatagramPacket data = new DatagramPacket(send, send.length, rec.getAddress(), 10468);
                    datagramSocket.send(data);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        udpStation.start();
    }
}