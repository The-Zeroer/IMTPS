package com.thezeroer.imtps.server.datapacket.databody;

import com.thezeroer.imtps.server.datapacket.DataPacket;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * 文件数据正文
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/08/02
 */
public class FileDataBody extends AbstractDataBody<File> {
    private static String fileCachePath;
    private long size;
    private String srcFileName;

    private RandomAccessFile randomAccessFile;
    private FileChannel fileChannel;

    static {
        try {
            setFileCachePath(".\\ImtpsFileCache\\");
        } catch (Exception e) {
            fileCachePath = ".\\";
        }
    }

    public FileDataBody() {}
    public FileDataBody(File file) throws FileNotFoundException {
        if (file == null || !file.exists()) {
            throw new FileNotFoundException();
        }
        data = file;
        size = file.length();
        srcFileName = file.getName();
    }

    public static void setFileCachePath(String fileCachePath) {
        FileDataBody.fileCachePath = fileCachePath;
        File filePath = new File(fileCachePath);
        if (!filePath.exists()) {
            filePath.mkdir();
        }
    }

    public String getSrcFileName() {
        return srcFileName;
    }

    @Override
    public void encode(ByteBuffer output) throws Exception {
        fileChannel.read(output);
    }
    @Override
    public void decode(ByteBuffer input) throws Exception {
        fileChannel.write(input);
    }

    @Override
    public void prepareEncode() throws Exception {
        randomAccessFile = new RandomAccessFile(data, "r");
        fileChannel = randomAccessFile.getChannel();
    }
    @Override
    public void prepareDecode(long size) throws Exception {
        this.size = size;
        data = createTempFile();
        randomAccessFile = new RandomAccessFile(data, "rw");
        fileChannel = randomAccessFile.getChannel();
    }
    @Override
    public void finishDecode() {
        size = data.length();
    }
    @Override
    public void release() {
        try {
            if (fileChannel != null) {
                fileChannel.close();
            }
            if (randomAccessFile != null) {
                randomAccessFile.close();
            }
        } catch (IOException ignored) {}
    }

    @Override
    public TYPE getType() {
        return TYPE.File;
    }
    @Override
    public long getSize() {
        return size;
    }

    @Override
    public byte[] getMetadata() {
        return srcFileName.getBytes(StandardCharsets.UTF_8);
    }
    @Override
    public void setMetadata(byte[] metadata) {
        srcFileName = new String(metadata, StandardCharsets.UTF_8);
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

        File file = new File(fileCachePath, srcFileName);
        for (int counter = 1; file.exists(); counter++) {
            file = new File(fileCachePath, baseName + "(" + counter + ")" + extension);
        }
        return file;
    }

    @Override
    public String toString() {
        if (data != null) {
            return "(srcFileName=" + srcFileName + ", fileSize=" + DataPacket.formatBytes(size) + ", currentPath=" + data.getAbsolutePath() + ")";
        } else {
            return super.toString();
        }
    }
}
