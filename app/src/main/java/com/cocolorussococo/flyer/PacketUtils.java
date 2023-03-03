package com.cocolorussococo.flyer;

import java.net.DatagramPacket;
import com.cocolorussococo.flyer.Host.*;

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
        packet[0] = (byte) ((newPort  >>> 8) & 255);
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

}
