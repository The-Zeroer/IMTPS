package com.thezeroer.imtps.client.datapacket;

import java.nio.charset.StandardCharsets;

/**
 * 控制包
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/16
 */
public class ControlPacket {
    public static final int BASIC_HEADER_SIZE = 2;
    public static class WAY {
        public static final byte HEART_BEAT = 1;
        public static final byte TOKEN = 2;
        public static final byte PORT_DATA_BASIC = 111;
        public static final byte PORT_DATA_FILE = 112;
        public static final byte READY_DATA_BASIC = 121;
        public static final byte READY_DATA_FILE = 122;
    }
    private final byte[] hander = new byte[BASIC_HEADER_SIZE];
    private final byte[] content;

    public ControlPacket(byte way) {
        hander[0] = way;
        content = null;
    }

    public ControlPacket(byte way, byte[] content) {
        hander[0] = way;
        hander[1] = (byte) content.length;
        this.content = content;
    }

    public byte getWay() {
        return hander[0];
    }
    public byte getSize() {
        return hander[1];
    }
    public byte[] getHander() {
        return hander;
    }
    public byte[] getContent() {
        return content;
    }

    @Override
    public String toString() {
        if (content != null) {
            return "[Way=" + hander[0] + ", Size=" + hander[1] + ", Content=" + new String(content, StandardCharsets.UTF_8) + "]";
        } else {
            return "[Way=" + hander[0] + ", Size=" + hander[1] + "]";
        }
    }
}
