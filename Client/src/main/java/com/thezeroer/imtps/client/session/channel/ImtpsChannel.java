package com.thezeroer.imtps.client.session.channel;

import com.thezeroer.imtps.client.datapacket.databody.AbstractDataBody;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.EnumMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * IMTPS通道
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/29
 */
public class ImtpsChannel {
    public enum TYPE {
        Control,
        DataBasic,
        DataFile,
        DataMedia,
    }
    public enum STATUS {
        Accepted, // 新建立的连接
        Filtered, // 通过过滤
        Handshaking, // 正在握手
        Handshaked, // 握手完成

        Unconnected, // 未连接
        Connecting, // 连接中
        Connected, // 已连接
    }

    protected static final EnumMap<AbstractDataBody.TYPE, TYPE> typeEnumMap = new EnumMap<>(AbstractDataBody.TYPE.class);
    protected final TYPE type;
    protected volatile STATUS status;
    protected final AtomicBoolean reading, writing;
    protected long lastActivityTime;

    protected SelectionKey selectionKey;
    protected SocketChannel socketChannel;
    protected InetSocketAddress socketAddress;
    protected InetAddress inetAddress;

    static {
        typeEnumMap.put(AbstractDataBody.TYPE.Basic, TYPE.DataBasic);
        typeEnumMap.put(AbstractDataBody.TYPE.File, TYPE.DataFile);
    }

    public ImtpsChannel(TYPE type) {
        this.type = type;
        reading = new AtomicBoolean(false);
        writing = new AtomicBoolean(false);
    }
    public ImtpsChannel(TYPE type, SocketChannel socketChannel) throws IOException {
        this(type);
        this.socketChannel = socketChannel;
        this.socketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        this.inetAddress = socketAddress.getAddress();
    }
    public ImtpsChannel(TYPE type, SelectionKey selectionKey) throws IOException {
        this(type);
        this.selectionKey = selectionKey;
        this.socketChannel = (SocketChannel) selectionKey.channel();
        this.socketAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
        this.inetAddress = socketAddress.getAddress();
    }

    public ImtpsChannel updateLastActivityTime() {
        lastActivityTime = System.currentTimeMillis();
        return this;
    }
    public long getLastActivityTime() {
        return lastActivityTime;
    }
    public void channelClosed() throws IOException {
        status = STATUS.Unconnected;
    }

    public TYPE getType() {
        return type;
    }
    public STATUS getStatus() {
        return status;
    }
    public ImtpsChannel setStatus(STATUS status) {
        this.status = status;
        return this;
    }

    public boolean isReading() {
        return reading.get();
    }
    public void setReading(boolean reading) {
        this.reading.set(reading);
    }
    public boolean isWriting() {
        return writing.get();
    }
    public void setWriting(boolean writing) {
        this.writing.set(writing);
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }
    public SocketChannel getSocketChannel() {
        return socketChannel;
    }
    public InetSocketAddress getSocketAddress() {
        return socketAddress;
    }
    public InetAddress getInetAddress() {
        return inetAddress;
    }
    public ConcurrentLinkedQueue<?> getSendQueue() {
        return new ConcurrentLinkedQueue<>();
    }

    public static TYPE chooseType(AbstractDataBody.TYPE type) {
        return typeEnumMap.get(type);
    }
}
