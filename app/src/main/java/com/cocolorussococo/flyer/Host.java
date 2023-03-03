package com.cocolorussococo.flyer;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

public class Host {
    public enum DeviceTypes {
        PHONE,
        TABLET,
        WINDOWS
    }
    public enum PacketTypes {
        OFFER,
        FORGETME
    }
    final private InetAddress ip;
    final private String name;
    final private PacketTypes packetType;
    private int port;
    final private DeviceTypes deviceType;

    public Host(InetAddress ip, String name, int port, DeviceTypes type, PacketTypes packetType) {
        this.ip = ip;
        this.name = name;
        this.port = port;
        this.deviceType = type;
        this.packetType = packetType;
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

    public DeviceTypes getDeviceType() {
        return deviceType;
    }

    public PacketTypes getPacketType() {
        return packetType;
    }
    public void updatePort(int newPort) {
        port = newPort;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != Host.class) return false;
        Host host = (Host) obj;
        return this.ip.equals(host.getIp());
    }

    public static String getHostname(Context context) {
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) ?
            Settings.Secure.getString(context.getContentResolver(), "bluetooth_name") :
            Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME);
    }
    public static ArrayList<NetworkInterface> getActiveInterfaces() throws SocketException {
        ArrayList<NetworkInterface> interfaces = new ArrayList<>();

        for (Enumeration<NetworkInterface> it = NetworkInterface.getNetworkInterfaces(); it.hasMoreElements(); ) {
            NetworkInterface networkInterface = it.nextElement();

            boolean valid = false;
            if (networkInterface.isLoopback() || !networkInterface.isUp()) continue;
            for (InterfaceAddress addr : networkInterface.getInterfaceAddresses()) {
                if (addr.getBroadcast() == null) continue;
                if (addr.getAddress().isSiteLocalAddress()) valid = true;
            }

            if (valid) {
                interfaces.add(networkInterface);
            }
        }

        return interfaces;
    }
}
