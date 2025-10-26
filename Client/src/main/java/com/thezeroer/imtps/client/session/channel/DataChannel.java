package com.thezeroer.imtps.client.session.channel;

import com.thezeroer.imtps.client.datapacket.DataPacket;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 数据通道
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/22
 */
public class DataChannel extends ImtpsChannel {
    private final ConcurrentLinkedQueue<DataPacket> sendQueue;

    public DataChannel(TYPE type) {
        super(type);
        sendQueue = new ConcurrentLinkedQueue<>();
        this.status = STATUS.Unconnected;
    }

    @Override
    public void channelClosed() throws IOException {
        super.channelClosed();
        if (selectionKey != null) {
            selectionKey.cancel();
        }
        if (socketChannel != null) {
            socketChannel.close();
        }
        sendQueue.clear();
    }
    @Override
    public ConcurrentLinkedQueue<DataPacket> getSendQueue() {
        return sendQueue;
    }

    public DataChannel setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
        this.socketChannel = (SocketChannel) selectionKey.channel();
        this.status = STATUS.Connected;
        return this;
    }
    public DataChannel setSocketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        return this;
    }
}
