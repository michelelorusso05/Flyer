package com.cocolorussococo.flyer;

import com.cocolorussococo.flyer.Host.DeviceType;
import com.cocolorussococo.flyer.Host.PacketType;

import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;

public class PacketUtils {
    // Protocol versions
    /**
     * Discovery Protocol version. <br/>
     * 1.0: Initial version.
     */
    public static final byte DISCOVERY_PROTOCOL_VERSION = 0x01;
    /**
     * Flow Protocol version. <br/>
     * 1.1: Added support for text transfer. Now string lengths are sent as ints and not bytes.
     * <br />
     * 1.0: Initial version.
     */
    public static final byte FLOW_PROTOCOL_VERSION = 0x02;
    public enum FlowType {
        SINGLE_FILE,
        TEXT,
        MULTI_FILE,
        NOT_SUPPORTED
    }
    private static final FlowType[] cachedFlowTypes = FlowType.values();
    public static FlowType flowTypeFromInt(int x) {
        if (x < 0 || x >= cachedFlowTypes.length - 1) return FlowType.NOT_SUPPORTED;
        return cachedFlowTypes[x];
    }

    private static final DeviceType[] cachedDeviceTypes = DeviceType.values();

    public static DeviceType deviceTypeFromInt(int x) {
        if (x < 0 || x >= cachedDeviceTypes.length - 1) return DeviceType.UNKNOWN;
        return cachedDeviceTypes[x];
    }

    private static final PacketType[] cachedPacketTypes = PacketType.values();

    public static PacketType packetTypesFromInt(int x) {
        if (x < 0 || x >= cachedPacketTypes.length - 1) return PacketType.UNIMPLEMENTED;
        return cachedPacketTypes[x];
    }

    /**
     * Create the data for a new Discovery DatagramPacket.
     * @param port The advertised TCP port.
     * @param deviceType The device type.
     * @param packetType The packet type.
     * @param deviceName The device name.
     * @return An array of bytes that contain all the values in their respective places. This array
     * can be modified freely (for example, to update the port using PacketUtils#updatePort)
     * @see PacketUtils#updatePort(byte[], int)
     */
    public static byte[] encapsulate(
            int port,
            DeviceType deviceType,
            PacketType packetType,
            String deviceName
    ) {
        byte[] send = new byte[128];
        // Version (1 byte)
        send[0] = DISCOVERY_PROTOCOL_VERSION;
        // Port (2 bytes)
        send[1] = (byte) ((port >>> 8) & 255);
        send[2] = (byte) (port & 255);
        // Device type (1 byte)
        send[3] = (byte) deviceType.ordinal();
        // Packet type (1 byte)
        send[4] = (byte) packetType.ordinal();
        // Reserved (3 bytes)
        // Name (120 bytes or 60 chars)
        byte[] name = deviceName.substring(0, Math.min(60, deviceName.length())).getBytes();
        System.arraycopy(name, 0, send, 8, name.length);

        return send;
    }

    /**
     * Update the port in an already existing packet.
     * @param packet The old packet.
     * @param newPort The new port.
     */
    public static void updatePort(byte[] packet, int newPort) {
        packet[1] = (byte) ((newPort >>> 8) & 255);
        packet[2] = (byte) (newPort & 255);
    }

    /**
     * Deencapsulates all informations about a host in a new Host object.
     * @param datagramPacket The incoming DatagramPacket containing the Host's informations.
     * @return The newly created Host object.
     * @throws IllegalArgumentException If the incoming packet is malformed (i.e. is not of proper length or contains invalid values)
     */
    public static Host deencapsulate(DatagramPacket datagramPacket) throws IllegalArgumentException {
        byte[] packet = datagramPacket.getData();

        if (packet.length != 128) throw new IllegalArgumentException("Packet is not 128 bytes.");

        String name = new String(packet, 8, 120);
        int port = Byte.toUnsignedInt(packet[2]) + (Byte.toUnsignedInt(packet[1]) << 8);

        DeviceType deviceType = deviceTypeFromInt(packet[3]);
        PacketType packetType = packetTypesFromInt(packet[4]);

        if (packetType == PacketType.UNIMPLEMENTED) throw new IllegalArgumentException("Packet type is invalid or unsupported in this version.");

        return new Host(datagramPacket.getAddress(), name, port, deviceType, packetType);
    }

    /**
     * Check if two IP addresses are on the same subnet.
     * @param addr1 First host address.
     * @param addr2 Second host address.
     * @param mask The shared mask to compare.
     * @return true if the addresses are in the same network; false otherwise.
     */
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

    public static String ellipsize(String str, int max) {
        return str;
        /*
        if (str.length() <= max) return str;
        return str.substring(0, max).concat("…");
         */
    }
}
