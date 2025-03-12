package imtp.client.datapacket.databody;

import javax.crypto.Cipher;
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
    public void read(Cipher cipher, SocketChannel socketChannel, long size) throws IOException {
        if (size > Integer.MAX_VALUE || size < 0) {
            throw new IOException("Invalid size " + size);
        }
        textSize = (int) size;
        textData = new byte[textSize];
        ByteBuffer textBuffer = ByteBuffer.allocate(Math.min(textSize, BUFFER_MAX_SIZE));
        for (int residue = textSize, readCount = 0; residue > 0;residue -= readCount, readCount = 0) {
            if (residue < textBuffer.remaining()) {
                textBuffer.limit(residue);
            }
            while (textBuffer.hasRemaining()) {
                readCount += socketChannel.read(textBuffer);
            }
            textBuffer.flip().get(textData, textSize - residue, (Math.min(residue, textBuffer.remaining()))).clear();
        }
    }
    @Override
    public void write(Cipher cipher, SocketChannel socketChannel) throws IOException {
        ByteBuffer textBuffer = ByteBuffer.allocate(Math.min(textSize, BUFFER_MAX_SIZE));
        for (int residue = textSize, writeCount = 0; residue > 0;residue -= writeCount, writeCount = 0) {
            textBuffer.put(textData, textSize - residue, Math.min(residue, textBuffer.remaining())).flip();
            while (textBuffer.hasRemaining()) {
                writeCount += socketChannel.write(textBuffer);
            }
            textBuffer.clear();
        }
    }

    @Override
    public TextDataBody createNewInstance() {
        return new TextDataBody();
    }
}