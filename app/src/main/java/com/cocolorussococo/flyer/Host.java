package com.cocolorussococo.flyer;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class Host {
    final private InetAddress ip;
    final private String name;
    final private int port;
    final private int type;

    public Host(InetAddress ip, String name, int port, int type) {
        this.ip = ip;
        this.name = name;
        this.port = port;
        this.type = type;
    }

    public InetAddress getIp() {
        return ip;
    }

    public String getName() {
        return name;
    }

    public int getPort() {
        return port;
    }

    public int getType() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != Host.class) return false;
        Host host = (Host) obj;
        return this.ip.equals(host.getIp()) && this.port == host.getPort();
    }
    public static Pair<InetAddress, Boolean> getBroadcastEx() throws SocketException {
        InetAddress address = null;
        boolean hasMultipleInterfaces = false;
        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
            NetworkInterface networkInterface = en.nextElement();
            if (networkInterface.isLoopback()) continue;
            if (!networkInterface.isUp()) continue;
            for (InterfaceAddress a : networkInterface.getInterfaceAddresses()) {
                System.out.println(networkInterface.getName() + " " + networkInterface.isVirtual() + " " + a.getAddress());
                if (a.getAddress().isSiteLocalAddress() && a.getBroadcast() != null) {
                    if (address != null)
                        hasMultipleInterfaces = true;
                    else
                        address = a.getBroadcast();

                    // System.out.println(a.getAddress().getCanonicalHostName());
                }
            }
        }
        return new Pair<>(address, hasMultipleInterfaces);
    }
    public static InetAddress getBroadcast() throws SocketException {
        return getBroadcastEx().first;
    }
    public static String getHostname(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), "bluetooth_name");
    }
}
