package imtps.server.process;

import imtps.server.datapacket.DataPacket;
import imtps.server.session.ImtpsSession;
import imtps.server.util.Tool;
import imtps.server.view.AbstractTransmitView;

import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * IMTPS 任务
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/08/01
 */
public abstract class ImtpsTask {
    /** 默认模式<br>提交任务后立即返回 */
    public static final byte PATTERN_DEFAULT = 0;
    /** 队列模式<br>如果此时没有等待响应的任务，提交此任务后立即返回<br>否则加入请求队列后立即返回，等待响应后请求 */
    public static final byte PATTERN_QUEUE = 1;
    /** 等待模式<br>提交任务后等待数据 */
    public static final byte PATTERN_WAIT = 2;

    private final String taskId;
    private long waitingTime, expirationTime;
    private byte pattern = PATTERN_DEFAULT;
    private final AtomicBoolean live;

    private AbstractTransmitView sendView, receiveView;
    private ImtpsSession imtpsSession;

    public ImtpsTask() {
        taskId = createTaskId();
        waitingTime = 10000L;
        live = new AtomicBoolean(true);
    }

    public abstract DataPacket request();
    public abstract void response(DataPacket dataPacket);
    public void finish(boolean flag) {}

    /**
     * 设置等待时间，默认10秒
     *
     * @param seconds 秒
     * @return {@link ImtpsTask }
     */
    public ImtpsTask setWaitingTime(int seconds) {
        this.waitingTime = seconds * 1000L;
        return this;
    }
    /**
     * 设置模式，默认模式提交任务后立即返回
     *
     * @param pattern 模式
     * @return {@link ImtpsTask }
     */
    public ImtpsTask setPattern(byte pattern) {
        this.pattern = pattern;
        return this;
    }

    /**
     * 设置发送视图
     *
     * @param transmitView 传输视图 {@link AbstractTransmitView}
     * @return {@link ImtpsTask }
     */
    public ImtpsTask setSendView(AbstractTransmitView transmitView) {
        this.sendView = transmitView;
        return this;
    }
    /**
     * 设置接收视图
     *
     * @param transmitView 传输视图 {@link AbstractTransmitView}
     * @return {@link ImtpsTask }
     */
    public ImtpsTask setReceiveView(AbstractTransmitView transmitView) {
        this.receiveView = transmitView;
        return this;
    }
    public AbstractTransmitView getSendView() {
        return sendView;
    }
    public AbstractTransmitView getReceiveView() {
        return receiveView;
    }

    public String getTaskId() {
        return taskId;
    }
    public byte getPattern() {
        return pattern;
    }
    public long getWaitingTime() {
        return waitingTime;
    }
    public long getExpirationTime() {
        return expirationTime;
    }
    public void updateExpirationTime() {
        expirationTime = System.currentTimeMillis() + waitingTime;
    }

    public ImtpsTask setImtpsSession(ImtpsSession imtpsSession) {
        this.imtpsSession = imtpsSession;
        return this;
    }
    public ImtpsSession getImtpsSession() {
        return imtpsSession;
    }

    public boolean isLive() {
        return live.compareAndSet(true, false);
    }

    public static String createTaskId() {
        try {
            return Tool.getStringHashValue(System.currentTimeMillis() + UUID.randomUUID().toString(), "MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
