package com.gigabyte.rgbfusion.core;

import java.nio.charset.StandardCharsets;

/** A PC found by multicast discovery (Cmp_info_str in the original). */
public final class Device {
    public final String title;
    /** "ip:port" */
    public final String address;

    // UI state used by group mode
    public int pattern = -1;
    public boolean selectable;
    public boolean selected;

    public Device(String title, String address) {
        this.title = title;
        this.address = address;
    }

    public String ip() {
        int i = address.indexOf(':');
        return i < 0 ? address : address.substring(0, i);
    }

    /** Parses a discovery reply "title,ip,port"; returns null when malformed. */
    public static Device parseReply(byte[] data, int length) {
        String text = new String(data, 0, length, StandardCharsets.UTF_8);
        String[] p = text.split(",");
        if (p.length < 3) return null;
        String title = p[0].trim();
        String ip = p[1].trim();
        String port = p[2].trim();
        if (ip.isEmpty() || port.isEmpty()) return null;
        return new Device(title, ip + ":" + port);
    }

    @Override public boolean equals(Object o) {
        return o instanceof Device && ((Device) o).address.equals(address);
    }

    @Override public int hashCode() {
        return address.hashCode();
    }
}
