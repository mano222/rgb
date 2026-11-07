package com.gigabyte.rgbfusion.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Dependency-free self test (run with plain `java`, no JUnit needed). */
public class CoreSelfTest {
    static int checks = 0;

    static void eq(Object exp, Object act, String what) {
        checks++;
        if (exp == null ? act != null : !exp.equals(act)) {
            throw new AssertionError(what + ": expected <" + exp + "> but was <" + act + ">");
        }
    }

    static void yes(boolean c, String what) { eq(true, c, what); }

    static final String PC_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
          + "<LED_info_Easy Mode=\"0\" Br=\"2\" color=\"4294901760\" Pattern=\"4\" Profile=\"1\" "
          + "E_Time=\"07:30\" S_Time=\"22:15\" Othermode=\"8\" Sp=\"1\" Support_Flag=\"127\" "
          + "Password_Str=\"System.Xml.XmlAttribute\" />";

    public static void main(String[] args) throws Exception {
        deviceParsing();
        ledStateXml();
        ledStateEncoding();
        functionRules();
        httpAgainstFakePc();
        discoveryOverLoopback();
        System.out.println("ALL OK (" + checks + " checks)");
    }

    // ---------------------------------------------------------------------
    static void deviceParsing() {
        byte[] r = "MY-PC,192.168.1.20,9009".getBytes(StandardCharsets.UTF_8);
        Device d = Device.parseReply(r, r.length);
        eq("MY-PC", d.title, "title");
        eq("192.168.1.20:9009", d.address, "address");
        eq("192.168.1.20", d.ip(), "ip");
        byte[] bad = "only,two".getBytes(StandardCharsets.UTF_8);
        eq(null, Device.parseReply(bad, bad.length), "malformed reply");
        byte[] ws = " PC , 10.0.0.2 , 9010\r\n".getBytes(StandardCharsets.UTF_8);
        eq("10.0.0.2:9010", Device.parseReply(ws, ws.length).address, "whitespace trimmed");
        eq("http://1.2.3.4:9009//?Get_Type=0", Protocol.stateUrl("1.2.3.4:9009"), "state url");
        yes(!Protocol.needsPassword(null), "pw null");
        yes(!Protocol.needsPassword(""), "pw empty");
        yes(!Protocol.needsPassword("System.Xml.XmlAttribute"), "pw marker");
        yes(Protocol.needsPassword("1234"), "pw real");
    }

    static void ledStateXml() throws IOException {
        LedState s = LedState.fromXml(PC_XML.getBytes(StandardCharsets.UTF_8));
        eq(0, s.mode, "mode"); eq(2, s.brightness, "br"); eq(4294901760L, s.color, "color");
        eq(4, s.pattern, "pattern"); eq(1, s.profile, "profile"); eq("07:30", s.endTime, "E_Time");
        eq("22:15", s.startTime, "S_Time"); eq(8, s.otherMode, "othermode"); eq(1, s.speed, "sp");
        eq(127, s.supportFlag, "support"); eq("System.Xml.XmlAttribute", s.password, "pw");

        String out = s.toXml();
        eq("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><LED_info_Easy Pattern=\"4\" "
                + "Profile=\"1\" Mode=\"0\" Othermode=\"8\" color=\"4294901760\" S_Time=\"22:15\" "
                + "E_Time=\"07:30\" Br=\"2\" Sp=\"1\" Support_Flag=\"127\" "
                + "Password_Str=\"System.Xml.XmlAttribute\" />", out, "serialised xml");
        LedState back = LedState.fromXml(out.getBytes(StandardCharsets.UTF_8));
        eq(out, back.toXml(), "xml round trip");

        LedState esc = s.copy(); esc.password = "a&b<\"c\">";
        eq("a&b<\"c\">", LedState.fromXml(esc.toXmlBytes()).password, "escaping round trip");

        try { LedState.fromXml("<LED_info_Easy Mode=\"0\"/>".getBytes()); yes(false, "missing attrs"); }
        catch (IOException expected) { checks++; }
        try { LedState.fromXml("<other/>".getBytes()); yes(false, "no element"); }
        catch (IOException expected) { checks++; }
        try { LedState.fromXml("not xml".getBytes()); yes(false, "garbage"); }
        catch (IOException expected) { checks++; }
    }

    static void ledStateEncoding() throws IOException {
        LedState s = LedState.fromXml(PC_XML.getBytes(StandardCharsets.UTF_8));
        s.stateUrl = Protocol.stateUrl("1.2.3.4:9009");
        LedState d = LedState.decode(s.encode());
        eq(s.encode(), d.encode(), "encode/decode");
        eq(s.stateUrl, d.stateUrl, "url survives");
        eq(null, LedState.decode("garbage"), "decode garbage");
        eq(null, LedState.decode(null), "decode null");
        LedState noUrl = s.copy(); noUrl.stateUrl = null;
        eq(null, LedState.decode(noUrl.encode()).stateUrl, "null url");
    }

    static void functionRules() {
        LedFunction[] f = LedFunction.values();
        eq(13, f.length, "13 tiles");
        eq("breath", f[0].key, "first tile"); eq("prc", f[12].key, "last tile");
        // all enabled with every bit set
        for (LedFunction x : f) yes(x.isEnabled(127), x + " enabled @127");
        // only STILL and PROFILE_C stay enabled with no bits
        for (LedFunction x : f) {
            boolean always = x == LedFunction.STILL || x == LedFunction.PROFILE_C;
            eq(always, x.isEnabled(0), x + " @0");
        }
        // bit 0x01 off: AUTO, INTELLIGENT, DEMO, PRA, PRB disabled (original idx 2,7,9,10,11)
        int no01 = 127 & ~0x01;
        for (LedFunction x : new LedFunction[]{LedFunction.AUTO, LedFunction.INTELLIGENT,
                LedFunction.DEMO, LedFunction.PROFILE_A, LedFunction.PROFILE_B})
            yes(!x.isEnabled(no01), x + " off w/o 0x01");
        yes(LedFunction.BEAT.isEnabled(no01), "BEAT unaffected by 0x01");
        // bit 0x08 off: DFLASH + DEMO
        int no08 = 127 & ~0x08;
        yes(!LedFunction.DFLASH.isEnabled(no08), "dflash"); yes(!LedFunction.DEMO.isEnabled(no08), "demo");
        yes(LedFunction.AUTO.isEnabled(no08), "auto unaffected by 0x08");
        yes(!LedFunction.BEAT.isEnabled(127 & ~0x02), "beat"); yes(!LedFunction.BREATH.isEnabled(127 & ~0x04), "breath");
        yes(!LedFunction.FLASH.isEnabled(127 & ~0x10), "flash"); yes(!LedFunction.RANDOM.isEnabled(127 & ~0x20), "random");
        yes(!LedFunction.WAVE.isEnabled(127 & ~0x40), "wave");

        LedState s = new LedState();
        LedFunction.FLASH.applyTo(s);
        eq(0, s.mode, "mode after flash"); eq(4, s.pattern, "pattern after flash");
        yes(LedFunction.FLASH.isSelected(s), "flash selected"); yes(!LedFunction.STILL.isSelected(s), "still not");
        LedFunction.PROFILE_B.applyTo(s);
        eq(1, s.mode, "adv mode"); eq(2, s.profile, "adv profile"); eq(4, s.pattern, "pattern kept");
        yes(LedFunction.PROFILE_B.isSelected(s), "B selected"); yes(!LedFunction.FLASH.isSelected(s), "flash deselected in adv mode");
        eq(LedFunction.Next.OTHER, LedFunction.INTELLIGENT.next, "intelligent opens Other");
        eq(LedFunction.Next.COLOR, LedFunction.STILL.next, "still opens colour");
        eq(LedFunction.Next.UPLOAD, LedFunction.WAVE.next, "wave uploads directly");
        eq(OtherMode.SCHEDULE, OtherMode.fromValue(8), "other mode 8");
        eq(null, OtherMode.fromValue(6), "other mode 6 unknown");
    }

    // ---------------------------------------------------------------------
    static void httpAgainstFakePc() throws Exception {
        final AtomicReference<String> getLine = new AtomicReference<>();
        final AtomicReference<String> getUa = new AtomicReference<>();
        final AtomicReference<String> postLine = new AtomicReference<>();
        final AtomicReference<String> postBody = new AtomicReference<>();
        final AtomicReference<String> postType = new AtomicReference<>();
        final AtomicReference<String> postLen = new AtomicReference<>();
        final java.net.ServerSocket srv = new java.net.ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    java.net.Socket sk = srv.accept();
                    InputStream in = sk.getInputStream();
                    String line = readLine(in);
                    java.util.Map<String, String> h = new java.util.HashMap<>();
                    String l;
                    while ((l = readLine(in)).length() > 0) {
                        int i = l.indexOf(':');
                        h.put(l.substring(0, i).toLowerCase(), l.substring(i + 1).trim());
                    }
                    byte[] resp;
                    if (line.startsWith("GET")) {
                        getLine.set(line); getUa.set(h.get("user-agent"));
                        resp = PC_XML.getBytes(StandardCharsets.UTF_8);
                    } else {
                        postLine.set(line); postType.set(h.get("content-type")); postLen.set(h.get("content-length"));
                        int len = Integer.parseInt(h.get("content-length"));
                        byte[] body = new byte[len]; int off = 0;
                        while (off < len) { int n = in.read(body, off, len - off); if (n < 0) break; off += n; }
                        postBody.set(new String(body, StandardCharsets.UTF_8));
                        resp = "OK".getBytes();
                    }
                    String head = "HTTP/1.1 200 OK\r\nContent-Length: " + resp.length + "\r\nConnection: close\r\n\r\n";
                    sk.getOutputStream().write(head.getBytes());
                    sk.getOutputStream().write(resp);
                    sk.getOutputStream().flush();
                    sk.close();
                }
            } catch (IOException closed) { /* done */ }
        });
        t.setDaemon(true); t.start();
        try {
            String addr = "127.0.0.1:" + srv.getLocalPort();
            RgbFusionClient c = new RgbFusionClient();
            LedState s = c.download(addr);
            eq("GET //?Get_Type=0 HTTP/1.1", getLine.get(), "GET request line keeps the double slash");
            eq(Protocol.USER_AGENT, getUa.get(), "GET user agent");
            eq(4, s.pattern, "downloaded pattern");
            eq("http://" + addr + "//?Get_Type=0", s.stateUrl, "state url recorded");

            s.pattern = 0; s.color = 4278255360L; s.brightness = 3;
            c.upload(s);
            eq("POST //?Get_Type=0 HTTP/1.1", postLine.get(), "POST request line");
            eq("multipart/form-data;boundary=*****", postType.get(), "POST content type");
            eq(s.toXml(), postBody.get(), "POST body is the raw XML");
            eq(String.valueOf(s.toXmlBytes().length), postLen.get(), "POST content length");
            LedState seen = LedState.fromXml(postBody.get().getBytes(StandardCharsets.UTF_8));
            eq(4278255360L, seen.color, "colour reached the PC"); eq(3, seen.brightness, "brightness reached the PC");
        } finally {
            srv.close();
        }
        try { new RgbFusionClient(URL -> URL.openConnection(), 1500).download("127.0.0.1:1"); yes(false, "refused"); }
        catch (IOException expected) { checks++; }
    }

    static String readLine(InputStream in) throws IOException {
        StringBuilder b = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.append((char) c);
        }
        return b.toString();
    }

    // ---------------------------------------------------------------------
    static void discoveryOverLoopback() throws Exception {
        InetAddress lo = InetAddress.getLoopbackAddress();
        // pick two free UDP ports
        int pcPort, mobilePort;
        try (DatagramSocket a = new DatagramSocket(0, lo); DatagramSocket b = new DatagramSocket(0, lo)) {
            pcPort = a.getLocalPort(); mobilePort = b.getLocalPort();
        }
        // a fake PC listening for the keyword
        final List<String> keywords = new ArrayList<>();
        final DatagramSocket fakePc = new DatagramSocket(pcPort, lo);
        final CountDownLatch five = new CountDownLatch(5);
        Thread pcThread = new Thread(() -> {
            try {
                byte[] buf = new byte[256];
                while (true) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    fakePc.receive(p);
                    keywords.add(new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8));
                    five.countDown();
                    byte[] reply = "FAKE-PC,10.1.2.3,9009".getBytes();
                    // replies go to the phone's listening port
                    fakePc.send(new DatagramPacket(reply, reply.length, lo, mobilePort_));
                }
            } catch (IOException closed) { /* test over */ }
        });
        mobilePort_ = mobilePort;
        pcThread.setDaemon(true); pcThread.start();

        final List<Device> found = new ArrayList<>();
        final CountDownLatch gotDevice = new CountDownLatch(1);
        final CountDownLatch timedOut = new CountDownLatch(1);
        Discovery d = new Discovery(Discovery.DEFAULT_FACTORY, lo, pcPort, mobilePort, 1200);
        d.startReceiver(new Discovery.Listener() {
            @Override public void onDevice(Device dev) { found.add(dev); gotDevice.countDown(); }
            @Override public void onTimeout() { timedOut.countDown(); }
            @Override public void onError(Exception e) { e.printStackTrace(); }
        });
        // nothing sent yet -> must time out (not hang, not crash)
        yes(timedOut.await(4, TimeUnit.SECONDS), "receiver reports timeout");
        long t0 = System.nanoTime();
        d.sendKeyword();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        yes(ms >= 3900 && ms < 6000, "sender takes ~4 s for 5 packets at 1 s spacing, was " + ms);
        yes(five.await(2, TimeUnit.SECONDS), "PC saw 5 keyword packets");
        for (String k : keywords) eq("Ext_WEB_Multicast", k, "keyword text");
        yes(gotDevice.await(2, TimeUnit.SECONDS), "device found");
        eq("FAKE-PC", found.get(0).title, "found title"); eq("10.1.2.3:9009", found.get(0).address, "found addr");
        d.stopReceiver();
        fakePc.close();
        Thread.sleep(300);
    }

    static int mobilePort_;
}
