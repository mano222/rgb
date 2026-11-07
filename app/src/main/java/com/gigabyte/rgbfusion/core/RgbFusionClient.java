package com.gigabyte.rgbfusion.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/** HTTP side of the protocol (Http_Service / comm_fun in the original). */
public final class RgbFusionClient {

    /** Lets the Android layer pin connections to the Wi-Fi network. */
    public interface ConnectionFactory {
        URLConnection open(URL url) throws IOException;
    }

    private final ConnectionFactory factory;
    private final int timeoutMs;

    public RgbFusionClient() {
        this(URL::openConnection, Protocol.HTTP_TIMEOUT_MS);
    }

    public RgbFusionClient(ConnectionFactory factory, int timeoutMs) {
        this.factory = factory;
        this.timeoutMs = timeoutMs;
    }

    /** GET the current state of the PC at "ip:port". */
    public LedState download(String address) throws IOException {
        String url = Protocol.stateUrl(address);
        HttpURLConnection c = (HttpURLConnection) factory.open(new URL(url));
        try {
            c.setRequestProperty("User-Agent", Protocol.USER_AGENT);
            c.setRequestProperty("Content-type", "text/html; charset=UTF-8");
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.connect();
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            byte[] body = readAll(c.getInputStream());
            if (body.length == 0) throw new IOException("Empty answer");
            LedState s = LedState.fromXml(body);
            s.stateUrl = url;
            return s;
        } finally {
            c.disconnect();
        }
    }

    /** POST a state to {@code state.stateUrl}; the body is the raw XML bytes. */
    public void upload(LedState state) throws IOException {
        if (state.stateUrl == null) throw new IOException("No target URL");
        byte[] body = state.toXmlBytes();
        HttpURLConnection c = (HttpURLConnection) factory.open(new URL(state.stateUrl));
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setDoInput(true);
            c.setDoOutput(true);
            c.setUseCaches(false);
            c.setRequestMethod("POST");
            c.setRequestProperty("Connection", "Keep-Alive");
            c.setRequestProperty("Charset", "UTF-8");
            c.setRequestProperty("User-Agent", Protocol.USER_AGENT);
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Content-Type", "multipart/form-data;boundary=*****");
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body);
                out.flush();
            }
            // The original read (and ignored) the first 10 bytes of the answer.
            int code = c.getResponseCode();
            try (InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream()) {
                if (in != null) {
                    byte[] b = new byte[64];
                    while (in.read(b) > 0) { /* drain */ }
                }
            }
            if (code >= 400) throw new IOException("HTTP " + code);
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = i.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
