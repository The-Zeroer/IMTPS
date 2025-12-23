package com.thezeroer.imtps.server.datapacket;

import com.thezeroer.imtps.server.datapacket.databody.AbstractDataBody;
import com.thezeroer.imtps.server.process.TaskWrap;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据包
 *
 * @author NiZhanBo
 * @since 2025/06/29
 * @version 1.0.0
 */
public class DataPacket implements Cloneable{
    public static final byte BASIC_HEADER_LENGTH = 15 + Header.TASK_ID_LENGTH;
    public enum WAY {
        /** 默认 */
        DEFAULT,
        /** 服务器正常响应 */
        SERVER_OK,
        /** 服务器内部出错 */
        SERVER_ERROR,
        /** 服务器忙 */
        SERVER_BUSY,
        /** 服务不存在 */
        SERVER_NULL,
        /** 服务器拒绝 */
        SERVER_REFUSE,

        /** 发送数据 */
        DATA_SEND,
        /** 请求数据 */
        DATA_REQUEST,
        /** 修改数据 */
        DATA_CHANGE,
        /** 重置数据 */
        DATA_RESET,
        /** 更新数据 */
        DATA_UPDATE,
    }
    private static class Header implements Cloneable{
        private static final byte TASK_ID_LENGTH = 32;
        private long time;
        private short way, tailLength;
        private byte packetIndex, pathDepth, bodyCount;
        private short[] path;
        private byte[] taskId;
        private final List<Long> bodyIds, bodySizes;
        private final List<Short> bodyMeatLengths;

        public Header() {
            time = System.currentTimeMillis();
            taskId = new byte[Header.TASK_ID_LENGTH];
            bodyIds = new ArrayList<>();
            bodySizes = new ArrayList<>();
            bodyMeatLengths = new ArrayList<>();
        }
        @Override
        public Header clone() {
            try {
                return (Header) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }

    private Header header;
    private final List<AbstractDataBody<?>> bodys;
    private byte[] tail;
    private TaskWrap taskWrap;

    private DataPacket() {
        header = new Header();
        bodys = new ArrayList<>();
    }

    public static DataPacket build(WAY way, short... path) {
        DataPacket dataPacket = new DataPacket();
        dataPacket.header.way = (short) way.ordinal();
        if (path != null && path.length > 0) {
            if (path.length > Byte.MAX_VALUE) {
                throw new ArithmeticException("Path too large (max=%d)".formatted(Byte.MAX_VALUE));
            }
            dataPacket.header.path = path;
            dataPacket.header.pathDepth = (byte) path.length;
        }
        return dataPacket;
    }
    public DataPacket attach(AbstractDataBody<?>... bodys) {
        if (this.bodys.size() + bodys.length > Byte.MAX_VALUE) {
            throw new ArithmeticException("Bodys too large (max=%d)".formatted(Byte.MAX_VALUE));
        }
        this.bodys.addAll(List.of(bodys));
        for (AbstractDataBody<?> body : bodys) {
            header.bodyIds.add(body.getId());
            header.bodySizes.add(body.getSize());
            header.bodyMeatLengths.add(body.getMetaLength());
        }
        header.bodyCount = (byte) this.bodys.size();
        return this;
    }
    public DataPacket attach(byte[] tail) {
        if (tail.length > Short.MAX_VALUE) {
            throw new ArithmeticException("Tail too large (max=%d)".formatted(Short.MAX_VALUE));
        }
        this.tail = tail;
        header.tailLength = (short) tail.length;
        return this;
    }

    public DataPacket setTaskId(String taskId) {
        byte[] taskIdBytes = taskId.getBytes(StandardCharsets.UTF_8);
        if (taskIdBytes.length != Header.TASK_ID_LENGTH) {
            throw new ArithmeticException("TaskIdLength must be %d".formatted(Header.TASK_ID_LENGTH));
        }
        this.header.taskId = taskIdBytes;
        return this;
    }
    public DataPacket setTime(long time) {
        this.header.time = time;
        return this;
    }
    public DataPacket setPacketIndex(byte packetIndex) {
        this.header.packetIndex = packetIndex;
        return this;
    }

    public int getCurrenHeaderLength() {
        int length = BASIC_HEADER_LENGTH;
        if (header.path != null) {
            length += header.path.length * Short.BYTES;
        }
        length += header.bodyIds.size() * (Long.BYTES * 2 + Short.BYTES);
        return length;
    }
    public int getVariableHeaderLength() {
        return header.pathDepth * Short.BYTES + header.bodyCount * (Long.BYTES * 2 + Short.BYTES);
    }
    public AbstractDataBody<?> getBody() {
        if (bodys.isEmpty()) {
            return null;
        } else {
            return bodys.getFirst();
        }
    }
    public List<AbstractDataBody<?>> getBodys() {
        return bodys;
    }
    public byte getBodyCount() {
        return header.bodyCount;
    }
    public long getBodyId(int index) {
        return header.bodyIds.get(index);
    }
    public long getBodySize(int index) {
        return header.bodySizes.get(index);
    }
    public short getBodyMeatLength(int index) {
        return header.bodyMeatLengths.get(index);
    }
    public short getTailLength() {
        return header.tailLength;
    }
    public byte[] getTail() {
        return tail;
    }
    public WAY getWay() {
        return WAY.values()[header.way];
    }
    public byte getPathDepth() {
        return header.pathDepth;
    }
    public short[] getPath() {
        return header.path;
    }

    public long getTime() {
        return header.time;
    }
    public String getTaskId() {
        if (header.taskId == null) {
            return "";
        } else {
            return new String(header.taskId, StandardCharsets.UTF_8);
        }
    }
    public byte getPacketIndex() {
        return header.packetIndex;
    }
    public AbstractDataBody.TYPE getDataBodyType() {
        if (bodys.isEmpty()) {
            return AbstractDataBody.TYPE.Basic;
        } else if (bodys.size() == 1) {
            return bodys.getFirst().getType();
        } else {
            AbstractDataBody.TYPE type = AbstractDataBody.TYPE.Basic;
            for (AbstractDataBody<?> body : bodys) {
                if (body.getType().ordinal() >= type.ordinal()) {
                    type = body.getType();
                }
            }
            return type;
        }
    }

    public DataPacket setTaskWrap(TaskWrap taskWrap) {
        this.taskWrap = taskWrap;
        return setTaskId(taskWrap.getTask().getTaskId());
    }
    public TaskWrap getTaskWrap() {
        return taskWrap;
    }

    protected ByteBuffer headerToBuffer(ByteBuffer output) {
        output.putLong(header.time);
        output.putShort(header.way);
        output.putShort(header.tailLength);
        output.put(header.packetIndex);
        output.put(header.pathDepth);
        output.put(header.bodyCount);
        output.put(header.taskId);
        for (int i = 0; i < header.pathDepth; i++) {
            output.putShort(header.path[i]);
        }
        for (AbstractDataBody<?> body : bodys) {
            output.putLong(body.getId());
            output.putLong(body.getSize());
            output.putShort(body.getMetaLength());
        }
        return output;
    }
    protected static DataPacket basicHeaderFromBuffer(ByteBuffer input) {
        DataPacket dataPacket = new DataPacket();
        dataPacket.header.time = input.getLong();
        dataPacket.header.way = input.getShort();
        dataPacket.header.tailLength = input.getShort();
        dataPacket.header.packetIndex = input.get();
        dataPacket.header.pathDepth = input.get();
        dataPacket.header.bodyCount = input.get();
        dataPacket.header.taskId = new byte[Header.TASK_ID_LENGTH];
        input.get(dataPacket.header.taskId);
        return dataPacket;
    }
    protected void variableHeaderFormBuffer(ByteBuffer input) {
        header.path = new short[header.pathDepth];
        for (int i = 0; i < header.pathDepth; i++) {
            header.path[i] = input.getShort();
        }
        for (int i = 0; i < header.bodyCount; i++) {
            header.bodyIds.add(input.getLong());
            header.bodySizes.add(input.getLong());
            header.bodyMeatLengths.add(input.getShort());
        }
    }
    protected void setBodys(AbstractDataBody<?>... bodys) {
        this.bodys.addAll(List.of(bodys));
    }
    protected void setTail(byte[] tail) {
        this.tail = tail;
    }

    public static String formatByteSize(long bytes) {
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

    public static byte[] StringToBytes(String string) {
        if (string == null) {
            return new byte[0];
        }
        return string.getBytes(StandardCharsets.UTF_8);
    }
    public static String bytesToString(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public DataPacket clone() {
        try {
            DataPacket copy = (DataPacket) super.clone();
            if (this.header != null) {
                copy.header = this.header.clone();
            }
            if (this.tail != null) {
                copy.tail = this.tail.clone();
            }
            // 深拷贝 DataBody（前提是它实现了 Cloneable 并正确重写 clone()）
            for (AbstractDataBody<?> body : this.bodys) {
                copy.bodys.add(body.clone());
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{Time=").append(dateFormat.format(header.time)).append(", Way=").append(WAY.values()[header.way].name()).append(", Path=");
        if (header.path != null) {
            for (int i = 0; i < header.path.length; i++) {
                if (i > 0) {
                    sb.append("/");
                }
                sb.append(header.path[i]);
            }
        }
        sb.append(", TaskId=").append(getTaskId()).append(", PacketIndex=").append(header.packetIndex).append(", Bodys=[");
        for (AbstractDataBody<?> body : bodys) {
            sb.append("name=").append(body.getClass().getSimpleName());
            sb.append(", size=").append(body.getSize());
            sb.append(", content=").append(body).append(";");
        }
        if (!bodys.isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append("]}").toString();
    }
}
