package imtps.client.datapacket.databody;

import imtps.client.process.AbstractTransferSchedule;
import imtps.client.security.SecureManager;
import imtps.client.util.Tool;

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
import java.nio.charset.StandardCharsets;

public class FileDataBody extends AbstractDataBody<File> {
    private static final int BUFFER_MAX_SIZE = 512*1024;
    private static String fileCachePath;

    private long fileSize;
    private File file;
    private String srcFileName;

    static {
        try {
            setFileCachePath(".\\ImtpsFileCache\\");
        } catch (FileNotFoundException e) {
            fileCachePath = ".\\";
            throw new NullPointerException("file is null");
        }
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

    public FileDataBody() {}
    public FileDataBody(File file) {
        if (file == null) {
            throw new NullPointerException("file is null");
        }
        this.file = file;
        fileSize = file.length();
        srcFileName = file.getName();
    }

    public void moveFile(File destFile) throws IOException {
        Tool.moveFile(file, destFile);
        file = destFile;
    }
    public String getSrcFileName() {
        return srcFileName;
    }

    @Override
    public long getSize() {
        return srcFileName == null ? fileSize : fileSize + SecureManager.getBefitSize(srcFileName.getBytes(StandardCharsets.UTF_8).length) + 4;
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
        srcFileName = file.getName();
    }
    @Override
    public File getContent() {
        return file;
    }

    @Override
    public void read(Cipher cipher, SocketChannel socketChannel, long size, AbstractTransferSchedule abstractTransferSchedule) throws IOException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        ByteBuffer netBuffer = ByteBuffer.allocate(4);
        while (netBuffer.hasRemaining()) {
            socketChannel.read(netBuffer);
        }
        int fileNameSize = netBuffer.flip().getInt();
        int befitSize = SecureManager.getBefitSize(fileNameSize);
        netBuffer = ByteBuffer.allocate(befitSize);
        while (netBuffer.hasRemaining()) {
            socketChannel.read(netBuffer);
        }
        ByteBuffer appBuffer = ByteBuffer.allocate(befitSize);
        cipher.doFinal(netBuffer.flip(), appBuffer);
        srcFileName = new String(appBuffer.array(), 0, fileNameSize, StandardCharsets.UTF_8);
        fileSize = size - (befitSize + 4);
        file = createTempFile();

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw"); FileChannel fileChannel = raf.getChannel()) {
            long sumSize = fileSize < BUFFER_MAX_SIZE ? SecureManager.getBefitSize(fileSize) :
                    (fileSize / BUFFER_MAX_SIZE) * SecureManager.getBefitSize(BUFFER_MAX_SIZE) + SecureManager.getBefitSize(fileSize % BUFFER_MAX_SIZE);
            befitSize = SecureManager.getBefitSize(fileSize > BUFFER_MAX_SIZE ? BUFFER_MAX_SIZE : (int) fileSize);
            appBuffer = ByteBuffer.allocate(befitSize);
            netBuffer = ByteBuffer.allocate(befitSize);
            if (abstractTransferSchedule == null) {
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
                abstractTransferSchedule.setMessage("正在接收" + file.getName());
                abstractTransferSchedule.setSumSize(sumSize);
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
                    abstractTransferSchedule.updateProgress(readSize);
                }
                abstractTransferSchedule.transferFinish("接收完成" + file.getName());
            }
        } catch (IOException e) {
            file.delete();
            throw e;
        }
    }
    @Override
    public void write(Cipher cipher, SocketChannel socketChannel, AbstractTransferSchedule abstractTransferSchedule) throws IOException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        if (file == null || !file.exists()) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"); FileChannel fileChannel = raf.getChannel()) {
            byte[] fileNameBytes = srcFileName.getBytes(StandardCharsets.UTF_8);
            int befitSize = SecureManager.getBefitSize(fileNameBytes.length);
            ByteBuffer netBuffer = ByteBuffer.allocate(4 + befitSize);
            ByteBuffer appBuffer = ByteBuffer.allocate(befitSize);
            cipher.doFinal(appBuffer.put(fileNameBytes).flip(), netBuffer.putInt(fileNameBytes.length));
            netBuffer.flip();
            while (netBuffer.hasRemaining()) {
                socketChannel.write(netBuffer);
            }

            int bufferSize = fileSize > BUFFER_MAX_SIZE ? BUFFER_MAX_SIZE : (int) fileSize;
            appBuffer = ByteBuffer.allocate(bufferSize);
            netBuffer = ByteBuffer.allocate(SecureManager.getBefitSize(bufferSize));
            if (abstractTransferSchedule == null) {
                for (long residue = fileSize, putSize; residue > 0; residue -= putSize) {
                    putSize = fileChannel.read(appBuffer.clear());
                    cipher.doFinal(appBuffer.flip(), netBuffer.clear());
                    netBuffer.flip();
                    while (netBuffer.hasRemaining()) {
                        socketChannel.write(netBuffer);
                    }
                }
            } else {
                abstractTransferSchedule.setMessage("正在发送" + file.getName());
                abstractTransferSchedule.setSumSize(fileSize);
                for (long residue = fileSize, putSize; residue > 0; residue -= putSize) {
                    putSize = fileChannel.read(appBuffer.clear());
                    cipher.doFinal(appBuffer.flip(), netBuffer.clear());
                    netBuffer.flip();
                    while (netBuffer.hasRemaining()) {
                        socketChannel.write(netBuffer);
                    }
                    abstractTransferSchedule.updateProgress(putSize);
                }
                abstractTransferSchedule.transferFinish("发送完成" + file.getName());
            }
        }
    }
    @Override
    public FileDataBody createNewInstance() {
        return new FileDataBody();
    }

    private File createTempFile() {
        String baseName, extension = "";
        int dotIndex = srcFileName.lastIndexOf(".");
        if (dotIndex != -1) {
            baseName = srcFileName.substring(0, dotIndex);
            extension = srcFileName.substring(dotIndex);
        } else {
            baseName = srcFileName;
        }

        File file = new File(fileCachePath, System.currentTimeMillis() + "-" + srcFileName);
        for (int counter = 1; file.exists(); counter++) {
            file = new File(fileCachePath, System.currentTimeMillis() + "-" + baseName + "(" + counter + ")" + extension);
        }
        return file;
    }
}