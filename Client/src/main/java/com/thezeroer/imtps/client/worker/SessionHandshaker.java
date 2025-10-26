package com.thezeroer.imtps.client.worker;

import com.thezeroer.imtps.client.log.ImtpsLogger;
import com.thezeroer.imtps.client.security.ImtpsSecretKey;
import com.thezeroer.imtps.client.session.ImtpsSession;
import com.thezeroer.imtps.client.session.channel.AcceptChannel;
import com.thezeroer.imtps.client.session.channel.ImtpsChannel;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

/**
 * 会话握手器
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/05
 */
public class SessionHandshaker {
    private final SessionManager sessionManager;
    private final ImtpsLogger imtpsLogger;

    public SessionHandshaker(SessionManager sessionManager, ImtpsLogger imtpsLogger) {
        this.sessionManager = sessionManager;
        this.imtpsLogger = imtpsLogger;
    }

    public boolean transmit(AcceptChannel acceptChannel) throws IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
        SocketChannel socketChannel = acceptChannel.getSocketChannel();
        if (acceptChannel.getType() == ImtpsChannel.TYPE.Control) {
            KeyPair keyPair = ImtpsSecretKey.createKeyPair();
            ByteBuffer byteBuffer = ByteBuffer.wrap(keyPair.getPublic().getEncoded());
            socketChannel.write(byteBuffer);
            socketChannel.read(byteBuffer.clear());
            ImtpsSecretKey imtpsSecretKey = new ImtpsSecretKey(keyPair.getPrivate(), byteBuffer.flip().array());

            byte[] finishedMessage = imtpsSecretKey.getFinishedMessage();
            byteBuffer = ByteBuffer.allocate(ImtpsSecretKey.FINISHEDMESSAGE_LENGTH).put(finishedMessage).flip();
            socketChannel.write(byteBuffer);
            socketChannel.read(byteBuffer.clear());
            if (Arrays.equals(finishedMessage, byteBuffer.array())) {
                byteBuffer = ByteBuffer.allocate(ImtpsSecretKey.NONCE_LENGTH + ImtpsSession.SESSIONID_LENGTH + ImtpsSecretKey.TAG_LENGTH);
                socketChannel.read(byteBuffer);
                sessionManager.transmit(acceptChannel.setImtpsSecretKey(imtpsSecretKey).setString(new String(imtpsSecretKey.decrypt(byteBuffer.array()))), true);
                return true;
            } else {
                imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "与服务器握手失败[finishedMessage错误]");
                return false;
            }
        } else {
            if (sessionManager.getImtpsSession() != null) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(ImtpsSession.SESSIONID_LENGTH);
                socketChannel.read(byteBuffer);
                sessionManager.transmit(acceptChannel.setString(new String(byteBuffer.array())), false);
                return true;
            } else {
                return false;
            }
        }
    }
}
