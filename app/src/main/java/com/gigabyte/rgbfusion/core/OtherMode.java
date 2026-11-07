package com.gigabyte.rgbfusion.core;

/** "Intelligent" monitoring sub-modes, in the order of the original list. */
public enum OtherMode {
    CPU_TEMP   (2, "o1", "O1"),
    CPU_USAGE  (1, "o2", "O2"),
    CPU_FAN    (4, "o3", "O3"),
    SCHEDULE   (8, "o4", "O4"),
    LAN_SPEED  (5, "o5", "O5"),
    SYSTEM_TEMP(3, "o6", "O6");

    public final int value;
    public final String drawableKey;
    /** Prefix of the string resources: O1_str / O1_Desp ... */
    public final String stringKey;

    OtherMode(int value, String drawableKey, String stringKey) {
        this.value = value;
        this.drawableKey = drawableKey;
        this.stringKey = stringKey;
    }

    public static OtherMode fromValue(int v) {
        for (OtherMode m : values()) if (m.value == v) return m;
        return null;
    }
}
