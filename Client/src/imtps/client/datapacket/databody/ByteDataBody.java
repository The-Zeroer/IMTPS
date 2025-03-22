package imtps.client.datapacket.databody;

import imtps.client.process.AbstractTransferSchedule;
import imtps.client.security.SecureManager;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class ByteDataBody extends AbstractDataBody<byte[]> {
    private static final int BUFFER_MAX_SIZE = 64*1024;
    private byte[] data;

    public ByteDataBody(){}
    public ByteDataBody(byte[] data) {
        this.data = data;
    }

    @Override
    public long getSize() {
        if (data == null) {
            return 0;
        } else {
            return data.length;
        }
    }
    @Override
    public boolean baseLinkTransfer() {
        return data == null || data.length <= 1204 * 1204;
    }

    @Override
    public void setContent(byte[] content) {
        this.data = content;
    }
    @Override
    public byte[] getContent() {
        return data;
    }

    @Override
    public void read(Cipher cipher, SocketChannel socketChannel, long size, AbstractTransferSchedule abstractTransferSchedule) throws IOException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        if (size > Integer.MAX_VALUE || size < 0) {
            throw new IOException("Invalid size " + size);
        }
        data = new byte[(int) size];
        int sumSize = data.length < BUFFER_MAX_SIZE ? SecureManager.getBefitSize(data.length) :
                (data.length / BUFFER_MAX_SIZE) * SecureManager.getBefitSize(BUFFER_MAX_SIZE) + SecureManager.getBefitSize(data.length % BUFFER_MAX_SIZE);
        int befitSize = SecureManager.getBefitSize(Math.min(data.length, BUFFER_MAX_SIZE));
        ByteBuffer appBuffer = ByteBuffer.allocate(befitSize);
        ByteBuffer netBuffer = ByteBuffer.allocate(befitSize);
        for (int residue = sumSize, readSize = 0, sumGetSize = 0, getSize; residue > 0; residue -= readSize, sumGetSize += getSize, readSize = 0) {
            if (residue < netBuffer.remaining()) {
                netBuffer.limit(residue);
            }
            while (netBuffer.hasRemaining()) {
                readSize += socketChannel.read(netBuffer);
            }
            cipher.doFinal(netBuffer.flip(), appBuffer);
            netBuffer.clear();
            getSize = appBuffer.flip().remaining();
            appBuffer.get(data, sumGetSize, getSize).clear();
        }
    }
    @Override
    public void write(Cipher cipher, SocketChannel socketChannel, AbstractTransferSchedule abstractTransferSchedule) throws IOException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        int bufferSize = Math.min(data.length, BUFFER_MAX_SIZE);
        ByteBuffer appBuffer = ByteBuffer.allocate(bufferSize);
        ByteBuffer netBuffer = ByteBuffer.allocate(SecureManager.getBefitSize(bufferSize));
        for (int residue = data.length, putSize; residue > 0; residue -= putSize) {
            putSize = appBuffer.clear().put(data, data.length - residue, Math.min(residue, appBuffer.remaining())).flip().remaining();
            cipher.doFinal(appBuffer, netBuffer.clear());
            netBuffer.flip();
            while (netBuffer.hasRemaining()) {
                socketChannel.write(netBuffer);
            }
        }
    }

    @Override
    public AbstractDataBody<byte[]> createNewInstance() {
        return new ByteDataBody();
    }
}
