package com.gigabyte.rgbfusion.core;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Multicast discovery (rgbmain$7 listener + rgbmain$8 sender in the original).
 * Platform neutral: the Android layer supplies a {@link SocketFactory} that pins
 * the sockets to the Wi-Fi network.
 */
public final class Discovery {

    public interface Listener {
        /** Called on the receiver thread for every distinct device. */
        void onDevice(Device device);
        /** Called on the receiver thread when nothing arrived for RECEIVE_TIMEOUT_MS. */
        void onTimeout();
        /** Receiver stopped because of an unexpected error. */
        void onError(Exception e);
    }

    /** Creates unbound sockets so Android can bind them to a Network first. */
    public interface SocketFactory {
        MulticastSocket create() throws IOException;
        /** Interface to join the group on, or null for the platform default. */
        NetworkInterface multicastInterface();
    }

    public static final SocketFactory DEFAULT_FACTORY = new SocketFactory() {
        @Override public MulticastSocket create() throws IOException { return new MulticastSocket(null); }
        @Override public NetworkInterface multicastInterface() { return null; }
    };

    private final SocketFactory factory;
    private final InetAddress group;
    private final int pcPort;
    private final int mobilePort;
    private final int receiveTimeoutMs;

    private volatile boolean stopped;
    private volatile MulticastSocket receiver;
    private Thread receiverThread;

    public Discovery(SocketFactory factory) throws IOException {
        this(factory, InetAddress.getByName(Protocol.MULTICAST_GROUP), Protocol.PC_PORT,
                Protocol.MOBILE_PORT, Protocol.RECEIVE_TIMEOUT_MS);
    }

    /** Fully configurable (used by tests). */
    public Discovery(SocketFactory factory, InetAddress group, int pcPort, int mobilePort,
                     int receiveTimeoutMs) {
        this.factory = factory;
        this.group = group;
        this.pcPort = pcPort;
        this.mobilePort = mobilePort;
        this.receiveTimeoutMs = receiveTimeoutMs;
    }

    // -------------------------------------------------------------- receiver

    public synchronized void startReceiver(final Listener listener) {
        if (receiverThread != null) return;
        stopped = false;
        receiverThread = new Thread(() -> runReceiver(listener), "rgbfusion-discovery-rx");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public synchronized void stopReceiver() {
        stopped = true;
        MulticastSocket s = receiver;
        if (s != null) s.close();           // unblocks receive()
        receiverThread = null;
    }

    private void runReceiver(Listener listener) {
        MulticastSocket s = null;
        try {
            s = factory.create();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(mobilePort));
            if (group.isMulticastAddress()) join(s);
            receiver = s;
            s.setSoTimeout(receiveTimeoutMs);
            byte[] buf = new byte[4096];
            while (!stopped) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try {
                    s.receive(p);
                } catch (SocketTimeoutException e) {
                    if (!stopped) listener.onTimeout();
                    continue;
                }
                Device d = Device.parseReply(p.getData(), p.getLength());
                if (d != null) listener.onDevice(d);
            }
        } catch (Exception e) {
            if (!stopped) listener.onError(e);
        } finally {
            receiver = null;
            if (s != null) {
                try { if (group.isMulticastAddress()) leave(s); } catch (Exception ignored) { }
                s.close();
            }
        }
    }

    private void join(MulticastSocket s) throws IOException {
        NetworkInterface nif = factory.multicastInterface();
        if (nif != null) s.joinGroup(new InetSocketAddress(group, 0), nif);
        else s.joinGroup(group);
    }

    private void leave(MulticastSocket s) throws IOException {
        NetworkInterface nif = factory.multicastInterface();
        if (nif != null) s.leaveGroup(new InetSocketAddress(group, 0), nif);
        else s.leaveGroup(group);
    }

    // ---------------------------------------------------------------- sender

    /**
     * Sends the discovery keyword {@link Protocol#DISCOVERY_REPEAT} times, one per
     * second. Blocks the calling thread, so call it from a worker thread.
     */
    public void sendKeyword() throws IOException, InterruptedException {
        byte[] data = Protocol.DISCOVERY_KEYWORD.getBytes(StandardCharsets.UTF_8);
        MulticastSocket s = factory.create();
        try {
            s.setReuseAddress(true);
            try {
                s.bind(new InetSocketAddress(pcPort));   // same source port as the original
            } catch (IOException busy) {
                s.bind(new InetSocketAddress(0));        // port taken: any free port works too
            }
            NetworkInterface nif = factory.multicastInterface();
            if (nif != null && group.isMulticastAddress()) s.setNetworkInterface(nif);
            DatagramPacket p = new DatagramPacket(data, data.length, group, pcPort);
            for (int i = 0; i < Protocol.DISCOVERY_REPEAT; i++) {
                s.send(p);
                if (i < Protocol.DISCOVERY_REPEAT - 1) Thread.sleep(Protocol.DISCOVERY_INTERVAL_MS);
            }
        } finally {
            s.close();
        }
    }
}
