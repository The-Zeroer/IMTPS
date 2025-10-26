package com.thezeroer.imtps.server.session.channel;

import com.thezeroer.imtps.server.security.ImtpsSecretKey;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * 接受通道
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/22
 */
public class AcceptChannel extends ImtpsChannel {
    private ImtpsSecretKey imtpsSecretKey;
    private String string;

    public AcceptChannel(SocketChannel socketChannel, TYPE type) throws IOException {
        super(type, socketChannel);
        this.status = STATUS.Accepted;
    }

    @Override
    public void channelClosed() throws IOException {
        super.channelClosed();
        socketChannel.close();
    }
    @Override
    public AcceptChannel setStatus(STATUS status) {
        this.status = status;
        return this;
    }

    public AcceptChannel setImtpsSecretKey(ImtpsSecretKey imtpsSecretKey) {
        this.imtpsSecretKey = imtpsSecretKey;
        return this;
    }
    public AcceptChannel setString(String string) {
        this.string = string;
        return this;
    }
    public ImtpsSecretKey getImtpsSecretKey() {
        return imtpsSecretKey;
    }
    public String getString() {
        return string;
    }
}
