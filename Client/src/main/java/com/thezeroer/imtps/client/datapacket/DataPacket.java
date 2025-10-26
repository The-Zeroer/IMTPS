package com.thezeroer.imtps.client.datapacket;

import com.thezeroer.imtps.client.datapacket.databody.AbstractDataBody;
import com.thezeroer.imtps.client.process.task.AbstractTask;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;

/**
 * 数据包
 *
 * @author NiZhanBo
 * @since 2025/06/29
 * @version 1.0.0
 */
public final class DataPacket implements Cloneable{
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    public static final int BASIC_HEADER_SIZE = 72;
    public static class WAY {
        /** 默认 */
        public static final int DEFAULT = 0;
        /** 服务器正常响应 */
        public static final int SERVER_OK = 101;
        /** 服务器内部出错 */
        public static final int SERVER_ERROR = 102;
        /** 服务器忙 */
        public static final int SERVER_BUSY = 103;
        /** 服务不存在 */
        public static final int SERVER_NULL = 104;
        /** 服务器拒绝 */
        public static final int SERVER_REFUSE = 105;

        /** 发送数据 */
        public static final int DATA_SEND = 201;
        /** 请求数据 */
        public static final int DATA_REQUEST = 202;
        /** 修改数据 */
        public static final int DATA_CHANGE = 203;
        /** 重置数据 */
        public static final int DATA_RESET = 204;
        /** 更新数据 */
        public static final int DATA_UPDATE = 205;

        /** 发送聊天消息 */
        public static final int MESSAGE_SEND = 301;
        /** 请求聊天消息 */
        public static final int MESSAGE_REQUEST = 302;

        /** 登录 */
        public static final int ACCOUNT_LOGIN = 1001;
        /** 登出 */
        public static final int ACCOUNT_LOGOUT = 1002;
        /** 注册账号 */
        public static final int ACCOUNT_REGISTER = 1101;
        /** 注销账号 */
        public static final int ACCOUNT_CANCEL = 1102;
        /** 封禁账号 */
        public static final int ACCOUNT_BANNED = 1103;
        /** 解封账号 */
        public static final int ACCOUNT_UNBANNED = 1104;
        /** 解冻账号 */
        public static final int ACCOUNT_UNBLOCKED = 1105;
    }
    public static class TYPE {
        /** 默认 */
        public static final int DEFAULT = 0;
        /** 文本 */
        public static final int TEXT = 101;
        /** 文件 */
        public static final int FILE = 102;
        /** 图片 */
        public static final int IMAGE = 103;
        /** 音频 */
        public static final int AUDIO = 104;
        /** 视频 */
        public static final int VIDEO = 105;
        /** 原图 */
        public static final int IMAGE_ORIGINAL = 111;
        /** 缩略图 */
        public static final int IMAGE_THUMBNAIL = 112;

        /** 管理员账号 */
        public static final int ADMIN_ACCOUNT = 1001;
        /** 普通用户账号 */
        public static final int USER_ACCOUNT = 1002;
        /** 账号信息 */
        public static final int ACCOUNT_INFO = 1011;
        /** 账号ID */
        public static final int ACCOUNT_ID = 1012;
        /** 账号密码 */
        public static final int ACCOUNT_PASSWORD = 1013;
        /** 账号名 */
        public static final int ACCOUNT_NAME = 1014;
        /** 账号头像 */
        public static final int ACCOUNT_AVATAR = 1015;
        /** 账号通讯录 */
        public static final int ACCOUNT_CONTACTS = 1021;
        /** 通讯录用户 */
        public static final int CONTACTS_USER = 1022;
        /** 通讯录群组 */
        public static final int CONTACTS_GROUP = 1023;
        /** 组织架构 */
        public static final int ORG_STRUCTURE = 1051;
        /** 部门 */
        public static final int DEPARTMENT = 1052;
        /** 账号邮箱 */
        public static final int ACCOUNT_EMAIL = 1101;

        /** 登录令牌 */
        public static final int TOKEN_LOGIN = 2001;

        /** 考勤 */
        public static final int ATTENDANCE = 9001;
    }
    public static class EXTRA {
        /** 默认 */
        public static final int DEFAULT = 0;

        /** 否 */
        public static final int FALSE = 10;
        /** 是 */
        public static final int TRUE = 11;

        /** 基础的 */
        public static final int BASIC = 101;
        /** 详细的 */
        public static final int DETAILED = 102;
        /** 全部的 */
        public static final int ALL = 103;
        /** 这个 */
        public static final int THIS = 111;
        /** 那个 */
        public static final int THAT = 112;
        /** 其他的 */
        public static final int OTHER = 113;
        /** 向前 */
        public static final int FORWARD = 114;
        /** 向后 */
        public static final int BACKWARD = 115;

        /** 系统 */
        public static final int SYSTEM = 121;
        /** 消息 */
        public static final int MESSAGE = 122;

        /** 在线 */
        public static final int ONLINE = 131;
        /** 离线 */
        public static final int OFFLINE = 132;

        /** 增 */
        public static final int ADD = 201;
        /** 删 */
        public static final int DELETE = 202;
        /** 改 */
        public static final int EDIT = 203;
        /** 查 */
        public static final int FIND = 204;

        /** 需要验证 */
        public static final int NEEDVERIFY = 301;
        /** 格式错误 */
        public static final int FORMATERROR = 302;
    }

    private int way, type, extra;
    private long time, dataBodyId, dataBodySize;
    private short metadataLength;
    private byte[] taskId, dataTail;
    private AbstractDataBody<?> dataBody;

    private AbstractTask<?> task;

    private DataPacket() {
        taskId = new byte[32];
    }
    private DataPacket(int way, int type, int extra) {
        this.way = way;
        this.type = type;
        this.extra = extra;
        this.time = System.currentTimeMillis();
    }
    public static DataPacket build(int way) {
        return new DataPacket(way, TYPE.DEFAULT, EXTRA.DEFAULT);
    }
    public static DataPacket build(int way, int type) {
        return new DataPacket(way, type, EXTRA.DEFAULT);
    }
    public static DataPacket build(int way, int type, int extra) {
        return new DataPacket(way, type, extra);
    }

    public DataPacket attachDataBody(AbstractDataBody<?> dataBody) {
        this.dataBody = dataBody;
        this.dataBodyId = dataBody.getId();
        this.dataBodySize = dataBody.getSize();
        if (dataBody.getMetadata() instanceof byte[] metadata) {
            if (metadata.length > Short.MAX_VALUE) {
                throw new ArithmeticException("Metadata too large (max= " + Short.MAX_VALUE + ")");
            }
            metadataLength = (short) metadata.length;
        } else {
            metadataLength = 0;
        }
        return this;
    }

    ByteBuffer getDataPacketBasicHeader(ByteBuffer output) {
        return output.putInt(way).putInt(type).putInt(extra).putLong(time).putLong(dataBodyId).putLong(dataBodySize)
                .putShort(metadataLength).putShort(getDataTailLength()).put(taskId);
    }
    static DataPacket setDataPacketBasicHeader(ByteBuffer input) {
        DataPacket dataPacket = new DataPacket();
        dataPacket.way = input.getInt();
        dataPacket.type = input.getInt();
        dataPacket.extra = input.getInt();
        dataPacket.time = input.getLong();
        dataPacket.dataBodyId = input.getLong();
        dataPacket.dataBodySize = input.getLong();
        dataPacket.metadataLength = input.getShort();
        dataPacket.dataTail = new byte[input.getShort()];
        input.get(dataPacket.taskId);
        return dataPacket;
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
    public long getDataBodyId() {
        return dataBodyId;
    }
    public long getDataBodySize() {
        return dataBodySize;
    }
    public short getMetadataLength() {
        return metadataLength;
    }
    public short getDataTailLength() {
        if (dataTail == null) {
            return 0;
        } else {
            return (short) dataTail.length;
        }
    }
    public String getTaskId() {
        if (taskId == null) {
            return "";
        } else {
            return new String(taskId, StandardCharsets.UTF_8);
        }
    }
    public AbstractDataBody<?> getDataBody() {
        return dataBody;
    }
    public AbstractDataBody.TYPE getDataBodyType() {
        if (dataBody == null) {
            return AbstractDataBody.TYPE.Basic;
        } else {
            return dataBody.getType();
        }
    }
    public String getHeadCode() {
        return way + "-" + type + "-" + extra;
    }
    public byte[] getDataTail() {
        return dataTail;
    }

    public DataPacket setTaskId(String taskId) {
        byte[] taskIdBytes = taskId.getBytes(StandardCharsets.UTF_8);
        if (taskIdBytes.length != 32) {
            throw new ArithmeticException("TaskIdLength must be 32");
        }
        this.taskId = taskIdBytes;
        return this;
    }
    public DataPacket setDataTail(byte[] dataTail) {
        if (dataTail != null && dataTail.length > Short.MAX_VALUE) {
            throw new ArithmeticException("Tail too large (max= " + Short.MAX_VALUE + ")");
        }
        this.dataTail = dataTail;
        return this;
    }
    public DataPacket setTime(long time) {
        this.time = time;
        return this;
    }

    public DataPacket setTask(AbstractTask<?> task) {
        this.task = task;
        setTaskId(task.getTaskId());
        return this;
    }
    public AbstractTask<?> getTask() {
        return task;
    }

    public static String formatBytes(long bytes) {
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
            // 深拷贝数组字段
            if (this.taskId != null) {
                copy.taskId = this.taskId.clone();
            }
            if (this.dataTail != null) {
                copy.dataTail = this.dataTail.clone();
            }
            // 深拷贝 DataBody（前提是它实现了 Cloneable 并正确重写 clone()）
            if (this.dataBody != null) {
                copy.dataBody = this.dataBody.clone();
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[Way=").append(way).append(", Type=").append(type).append(", Extra=").append(extra).append(", Time=").append(dateFormat.format(time))
                .append(", DataBodyId=").append(dataBodyId).append(", DataBodySize=").append(formatBytes(dataBodySize)).append(", TaskId=")
                .append(new String(taskId, StandardCharsets.UTF_8));
        if (dataBody != null) {
            if (dataBody.getMetadata() instanceof byte[] metadata) {
                sb.append(", Metadata=").append(new String(metadata, StandardCharsets.UTF_8));
            }
            sb.append(", DataBody=").append(dataBody);
        }
        return sb.append("]").toString();
    }
}
