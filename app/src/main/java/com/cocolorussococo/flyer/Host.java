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
    /**
     * All known device types.
     */
    public enum DeviceType {
        PHONE,
        TABLET,
        WINDOWS,
        UNKNOWN
    }

    /**
     * All known packet types (as version 1 of the Discovery Protocol).
     */
    public enum PacketType {
        OFFER,
        FORGETME,
        UNIMPLEMENTED
    }
    final private InetAddress ip;
    final private String name;
    final private PacketType packetType;
    private int port;
    final private DeviceType deviceType;
    private long lastUpdated;

    public Host(InetAddress ip, String name, int port, DeviceType type, PacketType packetType) {
        this.ip = ip;
        this.name = name;
        this.port = port;
        this.deviceType = type;
        this.packetType = packetType;
        this.lastUpdated = System.currentTimeMillis();
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

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public PacketType getPacketType() {
        return packetType;
    }
    public void updatePort(int newPort) {
        port = newPort;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated() {
        lastUpdated = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != Host.class) return false;
        Host host = (Host) obj;
        return this.ip.equals(host.getIp());
    }

    /**
     * Get the device's name. For versions before Android 7.1 (API 25), the Bluetooth name is returned.
     * Otherwise, the Settings.Global.DEVICE_NAME is used.
     * @param context The application context.
     * @return The device's name.
     */
    public static String getHostname(Context context) {
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) ?
            Settings.Secure.getString(context.getContentResolver(), "bluetooth_name") :
            Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME);
    }

    /**
     * Get a list of NetworkInterfaces that are eligible for Discovery. <br/>
     * This includes interfaces that:
     * <ul>
     *     <li>are connected to a private network.</li>
     *     <li>are connected to a network that has a Broadcast address (if it doesn't, it's a mobile data connection)</li>
     * </ul>
     * @return A list of all found interfaces. It can be empty if none are found.
     * @throws SocketException If something went wrong when iterating through interfaces.
     */
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
