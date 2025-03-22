package imtps.client.datapacket.databody;

import imtps.client.process.AbstractTransferSchedule;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * 抽象数据体，如需自定义数据体可继承此类
 *
 * @author NiZhanBo
 * @since 2025/01/26
 * @version 1.0.0
 */
public abstract class AbstractDataBody<T> {
    /**
     * 获取该数据体的实际大小
     *
     * @return long
     */
    public abstract long getSize();

    /**
     * 是否通过BaseLink传输数据
     *
     * @return boolean
     */
    public abstract boolean baseLinkTransfer();
    /**
     * 设置内容
     *
     * @param content 内容
     */
    public abstract void setContent(T content);
    /**
     * 获取内容
     *
     * @return {@link T }
     */
    public abstract T getContent();

    /**
     * 从socketChannel中获取数据，填充该数据体
     *
     * @param cipher 解密密码
     * @param socketChannel 套接字通道
     * @param size 该数据体实际大小
     * @param abstractTransferSchedule 传输时间表 {@link AbstractTransferSchedule}
     * @throws IOException io异常
     * @throws ShortBufferException 短缓冲区异常
     * @throws IllegalBlockSizeException 非法区块大小异常
     * @throws BadPaddingException 错误填充异常
     */
    public abstract void read(Cipher cipher, SocketChannel socketChannel, long size, AbstractTransferSchedule abstractTransferSchedule) throws IOException, ShortBufferException, IllegalBlockSizeException, BadPaddingException;

    /**
     * 将该数据体数据向socketChannel中写
     *
     * @param cipher 加密密码
     * @param socketChannel 套接字通道
     * @param abstractTransferSchedule 传输时间表 {@link AbstractTransferSchedule}
     * @throws IOException io异常
     * @throws ShortBufferException 短缓冲区异常
     * @throws IllegalBlockSizeException 非法区块大小异常
     * @throws BadPaddingException 错误填充异常
     */
    public abstract void write(Cipher cipher, SocketChannel socketChannel, AbstractTransferSchedule abstractTransferSchedule) throws IOException, ShortBufferException, IllegalBlockSizeException, BadPaddingException;
    /**
     * 创建新实例，请返回此子类的新对象
     *
     * @return {@link AbstractDataBody }<{@link T }>
     */
    public abstract AbstractDataBody<T> createNewInstance();
}