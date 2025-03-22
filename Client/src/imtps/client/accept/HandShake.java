package imtps.client.accept;

import imtps.client.link.LinkManager;
import imtps.client.log.ImtpsLogger;
import imtps.client.security.SecureManager;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

/**
 * 握手
 *
 * @author NiZhanBo
 * @since 2025/03/18
 * @version 1.0.0
 */
public class HandShake {
    public static boolean execute(SocketChannel socketChannel, SecureManager secureManager, LinkManager linkManager, ImtpsLogger imtpsLogger, String linkName)
            throws IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        byte[] keyBytes = secureManager.getPublicKey().getEncoded();
        ByteBuffer byteBuffer = ByteBuffer.allocate(2 + keyBytes.length).putShort((short) keyBytes.length).put(keyBytes);
        socketChannel.write(byteBuffer.flip());

        byteBuffer = ByteBuffer.allocate(2);
        socketChannel.read(byteBuffer);
        byteBuffer = ByteBuffer.allocate(byteBuffer.flip().getShort());
        socketChannel.read(byteBuffer);
        SecretKey secretKey = secureManager.generateKey(byteBuffer.flip().array());

        byte[] finishedMessage = SecureManager.generateFinishedMessage(secretKey);
        byteBuffer = ByteBuffer.allocate(2 + finishedMessage.length).putShort((short) finishedMessage.length).put(finishedMessage);
        socketChannel.write(byteBuffer.flip());

        byteBuffer = ByteBuffer.allocate(2);
        socketChannel.read(byteBuffer);
        byteBuffer = ByteBuffer.allocate(byteBuffer.flip().getShort());
        socketChannel.read(byteBuffer);
        if (Arrays.equals(finishedMessage, byteBuffer.array())) {
            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "[$]与服务器握手成功", linkName);
            linkManager.register(socketChannel, secretKey, linkName);
            return true;
        } else {
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "[$]握手失败，finishedMessage错误", linkName);
            return false;
        }
    }
}
