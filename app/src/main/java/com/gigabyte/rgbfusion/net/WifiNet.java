package com.gigabyte.rgbfusion.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;

import com.gigabyte.rgbfusion.core.Discovery;
import com.gigabyte.rgbfusion.core.RgbFusionClient;

import java.io.IOException;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;

/**
 * Android 13 networking glue. The original app relied on the process default network
 * and on being allowed to do network I/O on the UI thread. Today the PC lives on Wi-Fi,
 * but the "default" network may be mobile data, so everything is pinned to the Wi-Fi
 * {@link Network} (when there is one) and multicast is enabled with a MulticastLock.
 */
public final class WifiNet {
    private final Network wifi;
    private final NetworkInterface wifiInterface;
    private final WifiManager.MulticastLock lock;

    @SuppressWarnings("deprecation") // getAllNetworks() is deprecated in API 31 but still works
    public WifiNet(Context context) {
        Context app = context.getApplicationContext();
        Network found = null;
        NetworkInterface nif = null;
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    found = n;
                    LinkProperties lp = cm.getLinkProperties(n);
                    if (lp != null && lp.getInterfaceName() != null) {
                        try {
                            nif = NetworkInterface.getByName(lp.getInterfaceName());
                        } catch (SocketException ignored) {
                            nif = null;
                        }
                    }
                    break;
                }
            }
        }
        wifi = found;
        wifiInterface = nif;
        WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        lock = wm == null ? null : wm.createMulticastLock("rgbfusion-multicast");
        if (lock != null) lock.setReferenceCounted(false);
    }

    public void acquire() {
        if (lock != null && !lock.isHeld()) lock.acquire();
    }

    public void release() {
        if (lock != null && lock.isHeld()) lock.release();
    }

    public RgbFusionClient.ConnectionFactory connectionFactory() {
        return url -> wifi != null ? wifi.openConnection(url) : url.openConnection();
    }

    public Discovery.SocketFactory socketFactory() {
        return new Discovery.SocketFactory() {
            @Override
            public MulticastSocket create() throws IOException {
                MulticastSocket s = new MulticastSocket(null);   // unbound, so it can be pinned first
                if (wifi != null) wifi.bindSocket(s);
                return s;
            }

            @Override
            public NetworkInterface multicastInterface() {
                return wifiInterface;
            }
        };
    }
}
