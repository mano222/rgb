package com.gigabyte.rgbfusion.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Lighting state exchanged with the PC (Easy_mode_str in the original).
 * Serialised as one {@code <LED_info_Easy .../>} element.
 */
public final class LedState {
    /** Pattern value that means "LEDs off". */
    public static final int PATTERN_OFF = 8;

    public long color;
    public int pattern;
    public int profile = 1;
    /** 0 = normal patterns, 1 = advanced profiles (A/B/C). */
    public int mode;
    public int otherMode;
    public String startTime = "";
    public String endTime = "";
    public int brightness;
    public int speed;
    public int supportFlag;
    public String password = "";

    /** Where this state came from / must be posted to (UL_str). */
    public String stateUrl;

    public LedState copy() {
        LedState s = new LedState();
        s.color = color; s.pattern = pattern; s.profile = profile; s.mode = mode;
        s.otherMode = otherMode; s.startTime = startTime; s.endTime = endTime;
        s.brightness = brightness; s.speed = speed; s.supportFlag = supportFlag;
        s.password = password; s.stateUrl = stateUrl;
        return s;
    }

    // ------------------------------------------------------------------ XML

    /** Same attribute order as the original Create_XML. */
    public String toXml() {
        StringBuilder b = new StringBuilder(256);
        b.append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>");
        b.append("<LED_info_Easy");
        attr(b, "Pattern", String.valueOf(pattern));
        attr(b, "Profile", String.valueOf(profile));
        attr(b, "Mode", String.valueOf(mode));
        attr(b, "Othermode", String.valueOf(otherMode));
        attr(b, "color", String.valueOf(color));
        attr(b, "S_Time", startTime);
        attr(b, "E_Time", endTime);
        attr(b, "Br", String.valueOf(brightness));
        attr(b, "Sp", String.valueOf(speed));
        attr(b, "Support_Flag", String.valueOf(supportFlag));
        attr(b, "Password_Str", password);
        b.append(" />");
        return b.toString();
    }

    public byte[] toXmlBytes() {
        return toXml().getBytes(StandardCharsets.UTF_8);
    }

    private static void attr(StringBuilder b, String name, String value) {
        b.append(' ').append(name).append("=\"");
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '&': b.append("&amp;"); break;
                    case '<': b.append("&lt;"); break;
                    case '>': b.append("&gt;"); break;
                    case '"': b.append("&quot;"); break;
                    default: b.append(c);
                }
            }
        }
        b.append('"');
    }

    /** Parses the PC's answer. Throws when the element or a numeric field is missing. */
    public static LedState fromXml(byte[] xml) throws IOException {
        return fromXml(new ByteArrayInputStream(xml));
    }

    public static LedState fromXml(InputStream in) throws IOException {
        final LedState s = new LedState();
        final boolean[] found = {false};
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.newSAXParser().parse(new InputSource(in), new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes a)
                        throws SAXException {
                    if (!"LED_info_Easy".equals(qName)) return;
                    try {
                        s.mode = Integer.parseInt(a.getValue("Mode"));
                        s.brightness = Integer.parseInt(a.getValue("Br"));
                        s.color = Long.parseLong(a.getValue("color"));
                        s.pattern = Integer.parseInt(a.getValue("Pattern"));
                        s.profile = Integer.parseInt(a.getValue("Profile"));
                        s.endTime = nz(a.getValue("E_Time"));
                        s.startTime = nz(a.getValue("S_Time"));
                        s.otherMode = Integer.parseInt(a.getValue("Othermode"));
                        s.speed = Integer.parseInt(a.getValue("Sp"));
                        s.supportFlag = Integer.parseInt(a.getValue("Support_Flag"));
                        s.password = nz(a.getValue("Password_Str"));
                        found[0] = true;
                    } catch (NumberFormatException e) {
                        throw new SAXException("bad LED_info_Easy attribute: " + e.getMessage());
                    }
                }
            });
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException("Cannot parse device state: " + e.getMessage(), e);
        }
        if (!found[0]) throw new IOException("No LED_info_Easy element in device answer");
        return s;
    }

    private static String nz(String v) { return v == null ? "" : v; }

    // ------------------------------------------------- Intent-safe encoding

    private static final char SEP = '\u0001';

    /** Compact string form used to hand a state to another Activity. */
    public String encode() {
        return color + "" + SEP + pattern + SEP + profile + SEP + mode + SEP + otherMode + SEP
                + nz(startTime) + SEP + nz(endTime) + SEP + brightness + SEP + speed + SEP
                + supportFlag + SEP + nz(password) + SEP + (stateUrl == null ? "" : stateUrl);
    }

    public static LedState decode(String text) {
        if (text == null) return null;
        String[] p = text.split(String.valueOf(SEP), -1);
        if (p.length < 12) return null;
        try {
            LedState s = new LedState();
            s.color = Long.parseLong(p[0]);
            s.pattern = Integer.parseInt(p[1]);
            s.profile = Integer.parseInt(p[2]);
            s.mode = Integer.parseInt(p[3]);
            s.otherMode = Integer.parseInt(p[4]);
            s.startTime = p[5];
            s.endTime = p[6];
            s.brightness = Integer.parseInt(p[7]);
            s.speed = Integer.parseInt(p[8]);
            s.supportFlag = Integer.parseInt(p[9]);
            s.password = p[10];
            s.stateUrl = p[11].isEmpty() ? null : p[11];
            return s;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
