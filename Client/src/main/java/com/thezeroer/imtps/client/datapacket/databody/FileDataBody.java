package com.thezeroer.imtps.client.datapacket.databody;

import com.thezeroer.imtps.client.datapacket.DataPacket;
import com.thezeroer.imtps.client.util.Tool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件数据正文
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/08/02
 */
public class FileDataBody extends AbstractDataBody<File> {
    private static Path fileCachePath = Path.of(System.getProperty("user.dir"));
    private long size;
    private String srcFileName;

    private RandomAccessFile randomAccessFile;
    private FileChannel fileChannel;

    static {
        try {
            setFileCachePath(fileCachePath.resolve("ImtpsFileCache"));
        } catch (IOException e) {
            e.printStackTrace();
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

    public static void setFileCachePath(Path fileCachePath) throws IOException {
        if (!Files.exists(fileCachePath)) {
            Files.createDirectories(fileCachePath);
        }
        FileDataBody.fileCachePath = fileCachePath;
    }

    public String getSrcFileName() {
        return srcFileName;
    }
    public FileDataBody setSrcFileName(String srcFileName) {
        this.srcFileName = srcFileName;
        return this;
    }

    public void moveFile(File destFile) throws IOException {
        Tool.moveFile(data, destFile);
        data = destFile;
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
    public FileDataBody setMetadata(byte[] metadata) {
        srcFileName = new String(metadata, StandardCharsets.UTF_8);
        return this;
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

        File file = fileCachePath.resolve(srcFileName).toFile();
        for (int counter = 1; file.exists(); counter++) {
            file = fileCachePath.resolve(baseName + "(" + counter + ")" + extension).toFile();
        }
        return file;
    }

    @Override
    public FileDataBody clone() {
        FileDataBody copy = (FileDataBody) super.clone();
        // 浅拷贝文件引用信息
        copy.srcFileName = this.srcFileName;
        copy.size = this.size;
        // 不克隆 FileChannel、RandomAccessFile（不可序列化）
        copy.randomAccessFile = null;
        copy.fileChannel = null;
        return copy;
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
