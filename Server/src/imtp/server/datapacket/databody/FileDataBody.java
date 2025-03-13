package imtp.server.datapacket.databody;

import imtp.server.process.TransferSchedule;
import imtp.server.security.Secure;
import imtp.server.util.Tool;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class FileDataBody extends AbstractDataBody<File> {
    private static final int BUFFER_MAX_SIZE = 512*1024;

    private static String fileCachePath;
    private long fileSize;
    private File file;

    static {
        try {
            setFileCachePath(".\\ImtpFileCache\\");
        } catch (FileNotFoundException e) {
            fileCachePath = ".\\";
            throw new NullPointerException("file is null");
        }
    }

    public FileDataBody() {}
    public FileDataBody(File file) {
        if (file == null) {
            throw new NullPointerException("file is null");
        }
        this.file = file;
        fileSize = file.length();
    }

    public static void setFileCachePath(String fileCachePath) throws FileNotFoundException {
        FileDataBody.fileCachePath = fileCachePath;
        File tempFile = new File(fileCachePath);
        if (!tempFile.exists()) {
            if (!tempFile.mkdir()) {
                throw new FileNotFoundException(fileCachePath);
            }
        }
    }
    public void moveFile(File destFile) throws IOException {
        Tool.moveFile(file, destFile);
        file = destFile;
    }

    @Override
    public long getSize() {
        return fileSize;
    }
    @Override
    public boolean baseLinkTransfer() {
        return false;
    }

    @Override
    public void setContent(File file) {
        if (file == null) {
            throw new NullPointerException("file is null");
        }
        this.file = file;
        fileSize = file.length();
    }
    @Override
    public File getContent() {
        return file;
    }

    @Override
    public void read(Cipher cipher, SocketChannel socketChannel, long size, TransferSchedule transferSchedule) throws IOException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        fileSize = size;
        file = new File(getTempFileName(socketChannel));
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw"); FileChannel fileChannel = raf.getChannel()) {
            if (cipher == null) {
                for (long residue = fileSize, readSize; residue > 0; residue -= readSize) {
                    readSize = fileChannel.transferFrom(socketChannel, fileSize - residue, residue);
                }
            } else {
                long sumSize = fileSize < BUFFER_MAX_SIZE ? Secure.getBefitSize(fileSize) :
                        (fileSize / BUFFER_MAX_SIZE) * Secure.getBefitSize(BUFFER_MAX_SIZE) + Secure.getBefitSize(fileSize % BUFFER_MAX_SIZE);
                int befitSize = Secure.getBefitSize(fileSize > BUFFER_MAX_SIZE ? BUFFER_MAX_SIZE : (int) fileSize);
                ByteBuffer appBuffer = ByteBuffer.allocate(befitSize);
                ByteBuffer netBuffer = ByteBuffer.allocate(befitSize);
                if (transferSchedule == null) {
                    for (long residue = sumSize, readSize = 0; residue > 0; residue -= readSize, readSize = 0) {
                        if (residue < netBuffer.remaining()) {
                            netBuffer.limit((int) residue);
                        }
                        while (netBuffer.hasRemaining()) {
                            readSize += socketChannel.read(netBuffer);
                        }
                        cipher.doFinal(netBuffer.flip(), appBuffer.clear());
                        netBuffer.clear();
                        fileChannel.write(appBuffer.flip());
                    }
                } else {
                    transferSchedule.setMessage("正在接收" + file.getName());
                    transferSchedule.setSumSize(sumSize);
                    for (long residue = sumSize, readSize = 0; residue > 0; residue -= readSize, readSize = 0) {
                        if (residue < netBuffer.remaining()) {
                            netBuffer.limit((int) residue);
                        }
                        while (netBuffer.hasRemaining()) {
                            readSize += socketChannel.read(netBuffer);
                        }
                        cipher.doFinal(netBuffer.flip(), appBuffer.clear());
                        netBuffer.clear();
                        fileChannel.write(appBuffer.flip());
                        transferSchedule.updateProgress(readSize);
                    }
                    transferSchedule.transferFinish("接收完成" + file.getName());
                }
            }
        } catch (IOException e) {
            file.delete();
            throw e;
        }
    }
    @Override
    public void write(Cipher cipher, SocketChannel socketChannel, TransferSchedule transferSchedule) throws IOException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        if (file == null || !file.exists()) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"); FileChannel fileChannel = raf.getChannel()) {
            if (cipher == null) {
                for (long residue = fileSize, writeCount; residue > 0; residue -= writeCount) {
                    writeCount = fileChannel.transferTo(fileSize - residue, residue, socketChannel);
                }
            } else {
                int bufferSize = fileSize > BUFFER_MAX_SIZE ? BUFFER_MAX_SIZE : (int) fileSize;
                ByteBuffer appBuffer = ByteBuffer.allocate(bufferSize);
                ByteBuffer netBuffer = ByteBuffer.allocate(Secure.getBefitSize(bufferSize));
                if (transferSchedule == null) {
                    for (long residue = fileSize, putSize; residue > 0; residue -= putSize) {
                        putSize = fileChannel.read(appBuffer.clear());
                        cipher.doFinal(appBuffer.flip(), netBuffer.clear());
                        netBuffer.flip();
                        while (netBuffer.hasRemaining()) {
                            socketChannel.write(netBuffer);
                        }
                    }
                } else {
                    transferSchedule.setMessage("正在发送" + file.getName());
                    transferSchedule.setSumSize(fileSize);
                    for (long residue = fileSize, putSize; residue > 0; residue -= putSize) {
                        putSize = fileChannel.read(appBuffer.clear());
                        cipher.doFinal(appBuffer.flip(), netBuffer.clear());
                        netBuffer.flip();
                        while (netBuffer.hasRemaining()) {
                            socketChannel.write(netBuffer);
                        }
                        transferSchedule.updateProgress(putSize);
                    }
                    transferSchedule.transferFinish("发送完成" + file.getName());
                }
            }
        }
    }
    @Override
    public FileDataBody createNewInstance() {
        return new FileDataBody();
    }

    private synchronized String getTempFileName(SocketChannel socketChannel) {
        try {
            return fileCachePath + Tool.getHashValue((String.valueOf(System.currentTimeMillis()) + UUID.randomUUID() + socketChannel).getBytes(), "MD5");
        } catch (NoSuchAlgorithmException e) {
            return fileCachePath + System.currentTimeMillis() + UUID.randomUUID();
        }
    }
}