package com.thezeroer.imtps.client.datapacket;

import com.thezeroer.imtps.client.datapacket.databody.AbstractDataBody;
import com.thezeroer.imtps.client.process.TaskWrap;
import com.thezeroer.imtps.client.process.task.AbstractTask;

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
        private byte packetIndex, pathCount, bodyCount;
        private short[] paths;
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

    public static DataPacket build(WAY way, short... paths) {
        DataPacket dataPacket = new DataPacket();
        dataPacket.header.way = (short) way.ordinal();
        if (paths != null && paths.length > 0) {
            if (paths.length > Byte.MAX_VALUE) {
                throw new ArithmeticException("Paths too large (max=%d)".formatted(Byte.MAX_VALUE));
            }
            dataPacket.header.paths = paths;
            dataPacket.header.pathCount = (byte) paths.length;
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
        if (header.paths != null) {
            length += header.paths.length * Short.BYTES;
        }
        length += header.bodyIds.size() * (Long.BYTES * 2 + Short.BYTES);
        return length;
    }
    public int getVariableHeaderLength() {
        return header.pathCount * Short.BYTES + header.bodyCount * (Long.BYTES * 2 + Short.BYTES);
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
    public byte getPathCount() {
        return header.pathCount;
    }
    public short[] getPaths() {
        return header.paths;
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
        output.put(header.pathCount);
        output.put(header.bodyCount);
        output.put(header.taskId);
        for (int i = 0; i < header.pathCount; i++) {
            output.putShort(header.paths[i]);
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
        dataPacket.header.pathCount = input.get();
        dataPacket.header.bodyCount = input.get();
        dataPacket.header.taskId = new byte[Header.TASK_ID_LENGTH];
        input.get(dataPacket.header.taskId);
        return dataPacket;
    }
    protected void variableHeaderFormBuffer(ByteBuffer input) {
        header.paths = new short[header.pathCount];
        for (int i = 0; i < header.pathCount; i++) {
            header.paths[i] = input.getShort();
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
        sb.append("{Time=").append(dateFormat.format(header.time)).append(", Way=").append(WAY.values()[header.way].name()).append(", Paths=");
        if (header.paths != null) {
            for (int i = 0; i < header.paths.length; i++) {
                sb.append(header.paths[i]);
                if (i < header.paths.length - 1) {
                    sb.append("-");
                }
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

//    public static final int BASIC_HEADER_SIZE = 72;
//    public static class WAY {
//        /** 默认 */
//        public static final int DEFAULT = 0;
//        /** 服务器正常响应 */
//        public static final int SERVER_OK = 101;
//        /** 服务器内部出错 */
//        public static final int SERVER_ERROR = 102;
//        /** 服务器忙 */
//        public static final int SERVER_BUSY = 103;
//        /** 服务不存在 */
//        public static final int SERVER_NULL = 104;
//        /** 服务器拒绝 */
//        public static final int SERVER_REFUSE = 105;
//
//        /** 发送数据 */
//        public static final int DATA_SEND = 201;
//        /** 请求数据 */
//        public static final int DATA_REQUEST = 202;
//        /** 修改数据 */
//        public static final int DATA_CHANGE = 203;
//        /** 重置数据 */
//        public static final int DATA_RESET = 204;
//        /** 更新数据 */
//        public static final int DATA_UPDATE = 205;
//
//        /** 发送聊天消息 */
//        public static final int MESSAGE_SEND = 301;
//        /** 请求聊天消息 */
//        public static final int MESSAGE_REQUEST = 302;
//
//        /** 登录 */
//        public static final int ACCOUNT_LOGIN = 1001;
//        /** 登出 */
//        public static final int ACCOUNT_LOGOUT = 1002;
//        /** 注册账号 */
//        public static final int ACCOUNT_REGISTER = 1101;
//        /** 注销账号 */
//        public static final int ACCOUNT_CANCEL = 1102;
//        /** 封禁账号 */
//        public static final int ACCOUNT_BANNED = 1103;
//        /** 解封账号 */
//        public static final int ACCOUNT_UNBANNED = 1104;
//        /** 解冻账号 */
//        public static final int ACCOUNT_UNBLOCKED = 1105;
//    }
//    public static class TYPE {
//        /** 默认 */
//        public static final int DEFAULT = 0;
//        /** 文本 */
//        public static final int TEXT = 101;
//        /** 文件 */
//        public static final int FILE = 102;
//        /** 图片 */
//        public static final int IMAGE = 103;
//        /** 音频 */
//        public static final int AUDIO = 104;
//        /** 视频 */
//        public static final int VIDEO = 105;
//        /** 原图 */
//        public static final int IMAGE_ORIGINAL = 111;
//        /** 缩略图 */
//        public static final int IMAGE_THUMBNAIL = 112;
//
//        /** 管理员账号 */
//        public static final int ADMIN_ACCOUNT = 1001;
//        /** 普通用户账号 */
//        public static final int USER_ACCOUNT = 1002;
//        /** 账号信息 */
//        public static final int ACCOUNT_INFO = 1011;
//        /** 账号ID */
//        public static final int ACCOUNT_ID = 1012;
//        /** 账号密码 */
//        public static final int ACCOUNT_PASSWORD = 1013;
//        /** 账号名 */
//        public static final int ACCOUNT_NAME = 1014;
//        /** 账号头像 */
//        public static final int ACCOUNT_AVATAR = 1015;
//        /** 账号通讯录 */
//        public static final int ACCOUNT_CONTACTS = 1021;
//        /** 通讯录用户 */
//        public static final int CONTACTS_USER = 1022;
//        /** 通讯录群组 */
//        public static final int CONTACTS_GROUP = 1023;
//        /** 组织架构 */
//        public static final int ORG_STRUCTURE = 1051;
//        /** 部门 */
//        public static final int DEPARTMENT = 1052;
//        /** 账号邮箱 */
//        public static final int ACCOUNT_EMAIL = 1101;
//
//        /** 登录令牌 */
//        public static final int TOKEN_LOGIN = 2001;
//
//        /** 考勤 */
//        public static final int ATTENDANCE = 9001;
//    }
//    public static class EXTRA {
//        /** 默认 */
//        public static final int DEFAULT = 0;
//
//        /** 否 */
//        public static final int FALSE = 10;
//        /** 是 */
//        public static final int TRUE = 11;
//
//        /** 基础的 */
//        public static final int BASIC = 101;
//        /** 详细的 */
//        public static final int DETAILED = 102;
//        /** 全部的 */
//        public static final int ALL = 103;
//        /** 这个 */
//        public static final int THIS = 111;
//        /** 那个 */
//        public static final int THAT = 112;
//        /** 其他的 */
//        public static final int OTHER = 113;
//        /** 向前 */
//        public static final int FORWARD = 114;
//        /** 向后 */
//        public static final int BACKWARD = 115;
//
//        /** 系统 */
//        public static final int SYSTEM = 121;
//        /** 消息 */
//        public static final int MESSAGE = 122;
//
//        /** 在线 */
//        public static final int ONLINE = 131;
//        /** 离线 */
//        public static final int OFFLINE = 132;
//
//        /** 增 */
//        public static final int ADD = 201;
//        /** 删 */
//        public static final int DELETE = 202;
//        /** 改 */
//        public static final int EDIT = 203;
//        /** 查 */
//        public static final int FIND = 204;
//
//        /** 需要验证 */
//        public static final int NEEDVERIFY = 301;
//        /** 格式错误 */
//        public static final int FORMATERROR = 302;
//    }
}
