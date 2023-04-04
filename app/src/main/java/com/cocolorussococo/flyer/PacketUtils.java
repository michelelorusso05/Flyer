package com.cocolorussococo.flyer;

import com.cocolorussococo.flyer.Host.DeviceTypes;
import com.cocolorussococo.flyer.Host.PacketTypes;

import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;

public class PacketUtils {
    private static final DeviceTypes[] cachedDeviceTypes = DeviceTypes.values();

    public static DeviceTypes deviceTypeFromInt(int x) {
        return cachedDeviceTypes[x];
    }

    private static final PacketTypes[] cachedPacketTypes = PacketTypes.values();

    public static PacketTypes packetTypesFromInt(int x) {
        return cachedPacketTypes[x];
    }

    public static byte[] encapsulate(
            int port,
            DeviceTypes deviceType,
            PacketTypes packetType,
            String deviceName
    ) {
        byte[] send = new byte[132];
        send[0] = (byte) ((port >>> 8) & 255);
        send[1] = (byte) (port & 255);
        send[2] = (byte) deviceType.ordinal();
        send[3] = (byte) packetType.ordinal();

        byte[] name = deviceName.substring(0, Math.min(64, deviceName.length())).getBytes();
        System.arraycopy(name, 0, send, 4, name.length);

        return send;
    }
    public static void updatePort(byte[] packet, int newPort) {
        packet[0] = (byte) ((newPort >>> 8) & 255);
        packet[1] = (byte) (newPort & 255);
    }
    public static Host deencapsulate(DatagramPacket datagramPacket) throws IllegalArgumentException {
        byte[] packet = datagramPacket.getData();

        if (packet.length != 132) throw new IllegalArgumentException("Packet is not 132 bytes.");

        String name = new String(packet, 4, 128);
        int port = Byte.toUnsignedInt(packet[1]) + (Byte.toUnsignedInt(packet[0]) << 8);

        if (packet[2] < 0 || packet[2] > 2 || packet[3] < 0 || packet[3] > 1)
            throw new IllegalArgumentException("Invalid values for type fields.");


        return new Host(datagramPacket.getAddress(), name, port, deviceTypeFromInt(packet[2]), packetTypesFromInt(packet[3]));
    }

    public boolean sameNetwork(InetAddress addr1, InetAddress addr2, short mask) {
        if (!(addr1 instanceof Inet4Address) || !(addr2 instanceof Inet4Address)) return false;

        int a1 = toBytes(addr1);
        int a2 = toBytes(addr2);

        int bitmask = ~(1 << (mask - 1));
        return (a1 & bitmask) == (a2 & bitmask);
    }
    private int toBytes(InetAddress address) {
        int ip = 0;
        for (byte b : address.getAddress())
            ip = (ip << 8) + b;

        return ip;
    }
}
