package com.gigabyte.rgbfusion.core;

/**
 * Wire-protocol constants of the "RGB Fusion" PC companion service
 * (recovered from the original APK, v17.0623.1).
 *
 * Discovery : UDP multicast. The phone sends {@link #DISCOVERY_KEYWORD} to
 *             {@link #MULTICAST_GROUP}:{@link #PC_PORT} (5 times, 1 s apart) and
 *             listens on {@link #MULTICAST_GROUP}:{@link #MOBILE_PORT} for
 *             replies of the form "title,ip,port".
 * Control   : plain HTTP on the port announced in the reply (normally 9009).
 *             GET  http://ip:port//?Get_Type=0  -> LED_info_Easy XML
 *             POST http://ip:port//?Get_Type=0  <- LED_info_Easy XML (raw body)
 */
public final class Protocol {
    private Protocol() {}

    public static final String MULTICAST_GROUP = "224.168.100.1";
    /** Port the PC listens on for the discovery keyword. */
    public static final int PC_PORT = 1980;
    /** Port the phone listens on for discovery replies. */
    public static final int MOBILE_PORT = 19801;
    public static final String DISCOVERY_KEYWORD = "Ext_WEB_Multicast";

    public static final int DISCOVERY_REPEAT = 5;
    public static final int DISCOVERY_INTERVAL_MS = 1000;
    /** Receive timeout of the listener (same as the original: 10 s). */
    public static final int RECEIVE_TIMEOUT_MS = 10000;

    /** The original used 100 s; a LAN device answers in milliseconds. */
    public static final int HTTP_TIMEOUT_MS = 15000;

    public static final String USER_AGENT = "Mozilla/4.0 (compatible; MSIE 6.0; Windows 2000)";

    /** Value the PC sends in Password_Str when no password is configured. */
    public static final String NO_PASSWORD_MARKER = "System.Xml.XmlAttribute";

    /** "ip:port" -> state URL. Note the double slash: the PC service expects it. */
    public static String stateUrl(String address) {
        return "http://" + address + "//?Get_Type=0";
    }

    /** True when a password prompt must be shown for this Password_Str value. */
    public static boolean needsPassword(String passwordStr) {
        return passwordStr != null
                && passwordStr.length() != 0
                && !NO_PASSWORD_MARKER.equals(passwordStr);
    }
}
