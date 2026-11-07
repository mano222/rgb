package com.gigabyte.rgbfusion.core;

/**
 * The 13 tiles of the "Mode" screen, in the original order, with the value they
 * set, the follow-up screen they open and the Support_Flag bit(s) that enable them.
 */
public enum LedFunction {
    //            key           adv    value  next            support bits that must be SET to enable
    BREATH     ("breath",       false,  1, Next.COLOR,  0x04),
    BEAT       ("beat",         false,  2, Next.COLOR,  0x02),
    AUTO       ("auto",         false,  9, Next.UPLOAD, 0x01),
    STILL      ("still",        false,  0, Next.COLOR,  0x00),
    FLASH      ("flash",        false,  4, Next.COLOR,  0x10),
    RANDOM     ("random",       false,  5, Next.UPLOAD, 0x20),
    WAVE       ("wave",         false,  6, Next.UPLOAD, 0x40),
    INTELLIGENT("intelligent",  false, 10, Next.OTHER,  0x01),
    DFLASH     ("dflash",       false, 11, Next.COLOR,  0x08),
    DEMO       ("demo",         false, 22, Next.UPLOAD, 0x01 | 0x08),
    PROFILE_A  ("pra",          true,   1, Next.UPLOAD, 0x01),
    PROFILE_B  ("prb",          true,   2, Next.UPLOAD, 0x01),
    PROFILE_C  ("prc",          true,   3, Next.UPLOAD, 0x00);

    public enum Next { UPLOAD, COLOR, OTHER }

    /** Resource-name key (drawable and string are derived from it by the UI layer). */
    public final String key;
    /** true for Advanced A/B/C (they set Mode=1 + Profile, not Pattern). */
    public final boolean advanced;
    public final int value;
    public final Next next;
    private final int requiredBits;

    LedFunction(String key, boolean advanced, int value, Next next, int requiredBits) {
        this.key = key;
        this.advanced = advanced;
        this.value = value;
        this.next = next;
        this.requiredBits = requiredBits;
    }

    /**
     * Original Get_Led_Fun_status: a tile is disabled when ANY of its bits is clear
     * (DEMO is disabled by bit 0x01 or bit 0x08 being clear).
     */
    public boolean isEnabled(int supportFlag) {
        return (supportFlag & requiredBits) == requiredBits;
    }

    /** Original Set_Current_Pattern / Set_Current_Pattern_Adv. */
    public boolean isSelected(LedState s) {
        return advanced ? (s.mode == 1 && s.profile == value)
                        : (s.mode != 1 && s.pattern == value);
    }

    /** Applies this tile to the state exactly like the original click handler. */
    public void applyTo(LedState s) {
        if (advanced) {
            s.mode = 1;
            s.profile = value;
        } else {
            s.mode = 0;
            s.pattern = value;
        }
    }
}
