package imtps.server.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * 工具
 *
 * @author NiZhanBo
 * @since 2025/02/26
 * @version 1.0.0
 */
public class Tool {
    private static int taskId = 0;

    public static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static synchronized String produceTaskId() {
        taskId++;
        try {
            return getHashValue((String.valueOf(System.currentTimeMillis() + taskId)).getBytes(), "MD5");
        } catch (NoSuchAlgorithmException ignored) {
            return String.valueOf(System.currentTimeMillis() + taskId);
        }
    }

    public static String produceToken(SelectionKey key, String... addition) {
        String data = key.toString() + System.currentTimeMillis() + Arrays.toString(addition);
        try {
            return getHashValue(data.getBytes(), "SHA-256");
        } catch (NoSuchAlgorithmException e) {
            return data;
        }
    }

    public static String getHashValue(byte[] data, String algorithm) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        md.update(data);
        byte[] hashValue = md.digest();
        StringBuilder result = new StringBuilder();
        for (byte b : hashValue) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    public static String getFileHashValue(File file, String algorithm) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        FileInputStream fis = new FileInputStream(file);
        FileChannel fc = fis.getChannel();
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
        while (fc.read(buffer) != -1) {
            buffer.flip();
            digest.update(buffer);
            buffer.clear();
        }
        fis.close();
        fc.close();
        return new BigInteger(1, digest.digest()).toString(16);
    }

    public static void moveFile(File srcFile, File destFile) throws IOException {
        FileChannel srcChannel = null, destChannel = null;
        FileOutputStream fos = null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(srcFile);
            srcChannel = fis.getChannel();
            fos = new FileOutputStream(destFile);
            destChannel = fos.getChannel();
            for (long residue = srcFile.length() ,fileSize = residue; residue > 0; ) {
                residue -= srcChannel.transferTo(fileSize - residue, residue, destChannel);
            }
            srcFile.delete();
        } finally {
            if (fos != null) {
                fos.close();
            }
            if (fis != null) {
                fis.close();
            }
            if (srcChannel != null) {
                srcChannel.close();
            }
            if (destChannel != null) {
                destChannel.close();
            }
        }
    }
}