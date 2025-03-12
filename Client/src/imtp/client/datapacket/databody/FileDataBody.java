package imtp.client.datapacket.databody;

import imtp.client.util.Tool;

import javax.crypto.Cipher;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class FileDataBody extends AbstractDataBody<File> {
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
    public void read(Cipher cipher, SocketChannel socketChannel, long size) throws IOException {
        file = new File(getTempFileName(socketChannel));
        try (RandomAccessFile raf = new RandomAccessFile(file, "w"); FileChannel fileChannel = raf.getChannel()) {
            fileSize = size;
            for (long residue = fileSize, readCount = 0; residue > 0; residue -= readCount) {
                readCount = fileChannel.transferFrom(socketChannel, fileSize - residue, residue);
            }
        } catch (IOException e) {
            file.delete();
            throw e;
        }
    }
    @Override
    public void write(Cipher cipher, SocketChannel socketChannel) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"); FileChannel fileChannel = raf.getChannel()) {
            for (long residue = fileSize, writeCount = 0; residue > 0; residue -= writeCount) {
                writeCount = fileChannel.transferTo(fileSize - residue, residue, socketChannel);
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