package imtp.server.datapacket.databody;

import imtp.server.process.TransferSchedule;
import imtp.server.security.Secure;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class TextDataBody extends AbstractDataBody<String> {
    private static final int BUFFER_MAX_SIZE = 64*1024;

    private int textSize;
    private byte[] textData;
    private String textContent;

    public TextDataBody() {}
    public TextDataBody(String text) {
        if (text == null || text.isEmpty()) {
            textSize = 0;
        } else {
            textContent = text;
            textData = text.getBytes(StandardCharsets.UTF_8);
            textSize = textData.length;
        }
    }

    @Override
    public long getSize() {
        return textSize;
    }
    @Override
    public boolean baseLinkTransfer() {
        return textSize <= 1024 * 1024;
    }

    @Override
    public void setContent(String text) {
        if (text == null || text.isEmpty()) {
            textSize = 0;
        } else {
            textContent = text;
            textData = text.getBytes(StandardCharsets.UTF_8);
            textSize = textData.length;
        }
    }
    @Override
    public String getContent() {
        if (textContent == null || textContent.isEmpty()) {
            if (textData == null) {
                textContent = "";
            } else {
                textContent = new String(textData, StandardCharsets.UTF_8);
            }
        }
        return textContent;
    }

    @Override
    public void read(Cipher cipher, SocketChannel socketChannel, long size, TransferSchedule transferSchedule) throws IOException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        if (size > Integer.MAX_VALUE || size < 0) {
            throw new IOException("Invalid size " + size);
        }
        textSize = (int) size;
        textData = new byte[textSize];
        int bufferSize = Math.min(textSize, BUFFER_MAX_SIZE);
        if (cipher == null) {
            ByteBuffer textBuffer = ByteBuffer.allocate(bufferSize);
            for (int residue = textSize, readCount = 0; residue > 0;residue -= readCount, readCount = 0) {
                if (residue < textBuffer.remaining()) {
                    textBuffer.limit(residue);
                }
                while (textBuffer.hasRemaining()) {
                    readCount += socketChannel.read(textBuffer);
                }
                textBuffer.flip().get(textData, textSize - residue, (Math.min(residue, textBuffer.remaining()))).clear();
            }
        } else {
            int sumSize = textSize < BUFFER_MAX_SIZE ? Secure.getBefitSize(textSize) :
                    (textSize / BUFFER_MAX_SIZE) * Secure.getBefitSize(BUFFER_MAX_SIZE) + Secure.getBefitSize(textSize % BUFFER_MAX_SIZE);
            int befitSize = Secure.getBefitSize(bufferSize);
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
                appBuffer.get(textData, sumGetSize, getSize).clear();
            }
        }
    }
    @Override
    public void write(Cipher cipher, SocketChannel socketChannel, TransferSchedule transferSchedule) throws IOException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        int bufferSize = Math.min(textSize, BUFFER_MAX_SIZE);
        if (cipher == null) {
            ByteBuffer textBuffer = ByteBuffer.allocate(bufferSize);
            for (int residue = textSize, writeCount = 0; residue > 0;residue -= writeCount, writeCount = 0) {
                textBuffer.clear().put(textData, textSize - residue, Math.min(residue, textBuffer.remaining())).flip();
                while (textBuffer.hasRemaining()) {
                    writeCount += socketChannel.write(textBuffer);
                }
            }
        } else {
            ByteBuffer appBuffer = ByteBuffer.allocate(bufferSize);
            ByteBuffer netBuffer = ByteBuffer.allocate(Secure.getBefitSize(bufferSize));
            for (int residue = textSize, putSize; residue > 0; residue -= putSize) {
                putSize = appBuffer.clear().put(textData, textSize - residue, Math.min(residue, appBuffer.remaining())).flip().remaining();
                cipher.doFinal(appBuffer, netBuffer.clear());
                netBuffer.flip();
                while (netBuffer.hasRemaining()) {
                    socketChannel.write(netBuffer);
                }
            }
        }
    }

    @Override
    public TextDataBody createNewInstance() {
        return new TextDataBody();
    }
}