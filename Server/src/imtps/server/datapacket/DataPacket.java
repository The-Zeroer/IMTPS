package imtps.server.datapacket;

import imtps.server.datapacket.code.Extra;
import imtps.server.datapacket.code.Type;
import imtps.server.datapacket.code.Way;
import imtps.server.datapacket.databody.AbstractDataBody;
import imtps.server.process.AbstractTransferSchedule;
import imtps.server.process.ProcessingHub;
import imtps.server.security.SecureManager;
import imtps.server.util.Tool;

import javax.crypto.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;

/**
 * 数据包
 *
 * @author NiZhanBo
 * @since 2025/01/26
 * @version 1.0.0
 */
public class DataPacket{
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final int DEF_HEADER_SIZE = 68;
    private static final int AES_HEADER_SIZE = SecureManager.getBefitSize(DEF_HEADER_SIZE + SecureManager.IV_LENGTH);

    private int way;
    private int type;
    private int extra;
    private long time;
    private long dataBodySize;
    private long dataBodyClassId;
    private byte[] taskIdBytes;
    private AbstractDataBody<?> dataBody;

    private SelectionKey selectionKey;
    private String UID;

    public DataPacket(){
        init(Way.DEFAULT, Type.DEFAULT, Extra.DEFAULT, null);
    }
    public DataPacket(int way){
        init(way, Type.DEFAULT, Extra.DEFAULT, null);
    }
    public DataPacket(int way, int type){
        init(way, type, Extra.DEFAULT, null);
    }
    public DataPacket(int way, int type, int extra){
        init(way, type, extra, null);
    }
    public DataPacket(int way, AbstractDataBody<?> dataBody){
        init(way, Type.DEFAULT, Extra.DEFAULT, dataBody);
    }
    public DataPacket(int way, int type, AbstractDataBody<?> dataBody){
        init(way, type, Extra.DEFAULT, dataBody);
    }
    public DataPacket(int way, int type, int extra, AbstractDataBody<?> dataBody){
        init(way, type, extra, dataBody);
    }

    public boolean read(SelectionKey selectionKey, ProcessingHub processingHub) throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        SecretKey aesKey = (SecretKey) selectionKey.attachment();
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        ByteBuffer netBuffer = ByteBuffer.allocate(AES_HEADER_SIZE);
        ByteBuffer appBuffer = ByteBuffer.allocate(AES_HEADER_SIZE);
        while (netBuffer.hasRemaining()) {
            if (socketChannel.read(netBuffer) == -1) {
                return false;
            }
        }
        byte[] iv = new byte[16];
        netBuffer.flip().get(iv);
        Cipher cipher = SecureManager.generateDecryptCipher(aesKey, iv);
        cipher.doFinal(netBuffer, appBuffer);
        readHeader(appBuffer);
        dataBody = DataBodyManager.getDataBody(dataBodyClassId);
        if (dataBody != null) {
            dataBody.read(cipher, socketChannel, dataBodySize, processingHub.getReceiveTransferSchedule(getTaskId()));
        }
        return true;
    }
    public void write(SelectionKey selectionKey, AbstractTransferSchedule abstractTransferSchedule) throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        if (taskIdBytes == null) {
            taskIdBytes = Tool.produceTaskId().getBytes(StandardCharsets.UTF_8);
        }
        SecretKey aesKey = (SecretKey) selectionKey.attachment();
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        byte[] iv = SecureManager.generateIv();
        Cipher cipher = SecureManager.generateEncryptCipher(aesKey, iv);
        ByteBuffer netBuffer = ByteBuffer.allocate(AES_HEADER_SIZE);
        ByteBuffer appBuffer = ByteBuffer.allocate(DEF_HEADER_SIZE);
        appBuffer.putInt(way).putInt(type).putInt(extra).putLong(time).putLong(dataBodySize).putLong(dataBodyClassId).put(taskIdBytes).flip();
        cipher.doFinal(appBuffer, netBuffer.put(iv));
        netBuffer.flip();
        while (netBuffer.hasRemaining()) {
            socketChannel.write(netBuffer);
        }
        if (dataBody != null) {
            dataBody.write(cipher, socketChannel, abstractTransferSchedule);
        }
    }
    private void readHeader(ByteBuffer appBuffer) {
        appBuffer.flip();
        way = appBuffer.getInt();
        type = appBuffer.getInt();
        extra = appBuffer.getInt();
        time = appBuffer.getLong();
        dataBodySize = appBuffer.getLong();
        dataBodyClassId = appBuffer.getLong();
        taskIdBytes = new byte[32];
        appBuffer.get(taskIdBytes);
    }

    public boolean baseLinkTransfer() {
        return dataBody == null || dataBody.baseLinkTransfer();
    }
    public Object getDataBodyContent () {
        return dataBody == null ? "" : dataBody.getContent();
    }

    public DataPacket setTaskId(String taskId) {
        if (taskId != null) {
            byte[] taskIdBytes = taskId.getBytes();
            if (taskIdBytes.length == 32) {
                this.taskIdBytes = taskIdBytes;
            } else {
                this.taskIdBytes = Tool.produceTaskId().getBytes(StandardCharsets.UTF_8);
            }
        }
        return this;
    }
    public String getTaskId(){
        return taskIdBytes == null ? "" : new String(taskIdBytes, StandardCharsets.UTF_8);
    }
    public DataPacket setSelectionKey(SelectionKey selectionKey){
        this.selectionKey = selectionKey;
        return this;
    }
    public SelectionKey getSelectionKey(){
        return selectionKey;
    }
    public DataPacket setUID(String UID){
        this.UID = UID;
        return this;
    }
    public String getUID(){
        return UID;
    }

    public String getHeadCode() {
        return way + "-" + type + "-" + extra;
    }
    public int getWay() {
        return way;
    }
    public int getType() {
        return type;
    }
    public int getExtra() {
        return extra;
    }
    public long getTime() {
        return time;
    }
    public long getDataBodySize() {
        return dataBodySize;
    }
    public long getDataBodyClassId() {
        return dataBodyClassId;
    }
    public byte[] getTaskIdBytes() {
        return taskIdBytes;
    }
    public AbstractDataBody<?> getDataBody() {
        return dataBody;
    }

    @Override
    public String toString() {
        String remoteAddress = "null";
        if (selectionKey != null) {
            try {
                remoteAddress = ((SocketChannel)selectionKey.channel()).getRemoteAddress().toString();
            } catch (IOException ignored) {}
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[UID=").append(UID).append("; RemoteAddress=").append(remoteAddress).append("; Way=")
                .append(way).append("; Type=").append(type).append("; Extra=").append(extra)
                .append("; Time=").append(dateFormat.format(time)).append("; DataSize=")
                .append(formatBytes(dataBodySize)).append("; TaskId=").append(getTaskId());
        if (dataBody != null && dataBody.getContent() instanceof String string) {
            stringBuilder.append("; Content=").append(string);
        }
        stringBuilder.append("]");
        return stringBuilder.toString();
    }

    private void init(int way, int type, int extra, AbstractDataBody<?> dataBody) {
        this.way = way;
        this.type = type;
        this.extra = extra;
        this.time = System.currentTimeMillis();
        if (dataBody != null) {
            this.dataBody = dataBody;
            dataBodySize = dataBody.getSize();
            dataBodyClassId = DataBodyManager.getClassId(dataBody);
        } else {
            dataBodySize = 0L;
        }
    }
    private static String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "0B";
        } else {
            String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
            int idx = (int) (Math.log(bytes) / Math.log(1024));
            if (idx < units.length) {
                return String.format("%.2f%s", bytes / Math.pow(1024, idx), units[idx]);
            } else {
                return "ERROR";
            }
        }
    }
}