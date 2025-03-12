package imtp.client.datapacket.databody;

import javax.crypto.Cipher;
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
    public void read(Cipher cipher, SocketChannel socketChannel, long size) throws IOException {
        if (size > Integer.MAX_VALUE || size < 0) {
            throw new IOException("Invalid size " + size);
        }
        data = new byte[(int) size];
        if (cipher == null) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(Math.min(data.length, BUFFER_MAX_SIZE));
            for (int residue = data.length, readCount = 0; residue > 0;residue -= readCount, readCount = 0) {
                if (residue < byteBuffer.remaining()) {
                    byteBuffer.limit(residue);
                }
                while (byteBuffer.hasRemaining()) {
                    readCount += socketChannel.read(byteBuffer);
                }
                byteBuffer.flip().get(data, data.length - residue, (Math.min(residue, byteBuffer.remaining()))).clear();
            }
        }
    }
    @Override
    public void write(Cipher cipher, SocketChannel socketChannel) throws IOException {
        if (cipher == null) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(Math.min(data.length, BUFFER_MAX_SIZE));
            for (int residue = data.length, writeCount = 0; residue > 0;residue -= writeCount, writeCount = 0) {
                byteBuffer.put(data, data.length - residue, Math.min(residue, byteBuffer.remaining())).flip();
                while (byteBuffer.hasRemaining()) {
                    writeCount += socketChannel.write(byteBuffer);
                }
                byteBuffer.clear();
            }
        }
    }

    @Override
    public AbstractDataBody<byte[]> createNewInstance() {
        return new ByteDataBody();
    }
}
