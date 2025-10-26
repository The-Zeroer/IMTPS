package com.thezeroer.imtps.client.session.channel;

import com.thezeroer.imtps.client.datapacket.ControlPacket;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 控制通道
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/22
 */
public class ControlChannel extends ImtpsChannel {
    private final ConcurrentLinkedQueue<ControlPacket> sendQueue;

    public ControlChannel(SelectionKey selectionKey) throws IOException {
        super(TYPE.Control, selectionKey);
        sendQueue = new ConcurrentLinkedQueue<>();
        this.status = STATUS.Connected;
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
    public ConcurrentLinkedQueue<ControlPacket> getSendQueue() {
        return sendQueue;
    }
}
