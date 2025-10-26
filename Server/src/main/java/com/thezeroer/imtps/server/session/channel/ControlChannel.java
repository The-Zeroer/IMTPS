package com.thezeroer.imtps.server.session.channel;

import com.thezeroer.imtps.server.datapacket.ControlPacket;

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
        status = STATUS.Connected;
    }

    @Override
    public void channelClosed() throws IOException {
        super.channelClosed();
        selectionKey.cancel();
        socketChannel.close();
        sendQueue.clear();
    }
    @Override
    public ConcurrentLinkedQueue<ControlPacket> getSendQueue() {
        return sendQueue;
    }
}
