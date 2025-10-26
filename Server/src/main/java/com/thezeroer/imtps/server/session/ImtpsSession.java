package com.thezeroer.imtps.server.session;

import com.thezeroer.imtps.server.datapacket.DataPacket;
import com.thezeroer.imtps.server.security.ImtpsSecretKey;
import com.thezeroer.imtps.server.session.channel.ControlChannel;
import com.thezeroer.imtps.server.session.channel.DataChannel;
import com.thezeroer.imtps.server.session.channel.ImtpsChannel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.EnumMap;

/**
 * IMTPS 会话
 *
 * @author NiZhanBo
 * @since 2025/06/29
 * @version 1.0.0
 */
public class ImtpsSession {
    public static final int SESSIONID_LENGTH = 64;

    private final String sessionId;
    private final ImtpsSecretKey imtpsSecretKey;
    private final EnumMap<ImtpsChannel.TYPE, ImtpsChannel> channelEnumMap;
    private final InetAddress remoteAddress, localAddress;
    private final long creationTime;
    private volatile String sessionName;

    private Object attachment;

    public ImtpsSession(SelectionKey selectionKey, ImtpsSecretKey imtpsSecretKey, String sessionId) throws IOException {
        this.sessionId = sessionId;
        this.imtpsSecretKey = imtpsSecretKey;
        channelEnumMap = new EnumMap<>(ImtpsChannel.TYPE.class);
        creationTime = System.currentTimeMillis();
        channelEnumMap.put(ImtpsChannel.TYPE.Control, new ControlChannel(selectionKey));
        channelEnumMap.put(ImtpsChannel.TYPE.DataBasic, new DataChannel(ImtpsChannel.TYPE.DataBasic));
        channelEnumMap.put(ImtpsChannel.TYPE.DataFile,  new DataChannel(ImtpsChannel.TYPE.DataFile));
        remoteAddress = ((InetSocketAddress) ((SocketChannel) selectionKey.channel()).getRemoteAddress()).getAddress();
        localAddress = ((InetSocketAddress) ((SocketChannel) selectionKey.channel()).getLocalAddress()).getAddress();
    }

    public long getCreationTime() {
        return creationTime;
    }
    public long getLastActivityTime(ImtpsChannel.TYPE type) {
        return channelEnumMap.get(type).getLastActivityTime();
    }
    public void updateLastActivityTime(ImtpsChannel.TYPE type) {
        channelEnumMap.get(type).updateLastActivityTime();
    }
    public void channelClosed(ImtpsChannel.TYPE type) throws IOException {
        channelEnumMap.get(type).channelClosed();
        if (type == ImtpsChannel.TYPE.Control) {
            channelEnumMap.get(ImtpsChannel.TYPE.DataBasic).channelClosed();
            channelEnumMap.get(ImtpsChannel.TYPE.DataFile).channelClosed();
        }
    }

    public ImtpsChannel getChannel(ImtpsChannel.TYPE type) {
        return channelEnumMap.get(type);
    }
    public ControlChannel getControlChannel() {
        return (ControlChannel) channelEnumMap.get(ImtpsChannel.TYPE.Control);
    }
    public DataChannel getDataChannel(DataChannel.TYPE type) {
        return (DataChannel) channelEnumMap.get(type);
    }
    public ImtpsSecretKey getImtpsSecretKey() {
        return imtpsSecretKey;
    }
    public String getSessionId() {
        return sessionId;
    }
    public InetAddress getRemoteAddress() {
        return remoteAddress;
    }
    public InetAddress getLocalAddress() {
        return localAddress;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }
    public String getSessionName() {
        return sessionName;
    }
    public void setAttachment(Object attachment) {
        this.attachment = attachment;
    }
    public Object getAttachment() {
        return attachment;
    }

    public void putSendQueue(DataPacket dataPacket) {
        ((DataChannel) channelEnumMap.get(ImtpsChannel.chooseType(dataPacket.getDataBodyType()))).getSendQueue().add(dataPacket);
    }
    public DataPacket getSendDataPacket(DataChannel.TYPE type) {
        return ((DataChannel) channelEnumMap.get(type)).getSendQueue().poll();
    }
}
