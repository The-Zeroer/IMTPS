package imtp.client.datapacket.databody;

import javax.crypto.Cipher;
import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * 抽象数据体
 *
 * @author NiZhanBo
 * @since 2025/01/26
 * @version 1.0.0
 */
public abstract class AbstractDataBody<T> {
    public abstract long getSize();
    public abstract boolean baseLinkTransfer();
    public abstract void setContent(T content);
    public abstract T getContent();
    public abstract void read(Cipher cipher, SocketChannel socketChannel, long size)throws IOException;
    public abstract void write(Cipher cipher, SocketChannel socketChannel)throws IOException;
    public abstract AbstractDataBody<T> createNewInstance();
}