package com.thezeroer.imtps.client.process.task;

import com.thezeroer.imtps.client.datapacket.DataPacket;
import com.thezeroer.imtps.client.util.Tool;
import com.thezeroer.imtps.client.view.AbstractTransmitView;

import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 抽象任务
 *
 * @author NiZhanBo
 * @since 2025/09/24
 * @version 1.0.0
 */
public abstract class AbstractTask<T> {
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
    protected T response;

    private AbstractTransmitView sendView, receiveView;

    public AbstractTask() {
        this(createTaskId());
    }
    public AbstractTask(String taskId) {
        if (taskId == null) {
            taskId = createTaskId();
        }
        this.taskId = taskId;
        waitingTime = 10000L;
        live = new AtomicBoolean(true);
    }


    public abstract T request() throws Exception;
    public abstract void response();
    public void finish(boolean flag) {}

    public abstract boolean putResponseData(DataPacket dataPacket);
    public T getResponseData() {
        return response;
    }

    /**
     * 设置等待时间，默认10秒
     *
     * @param seconds 秒
     * @return {@link ImtpsTask }
     */
    public AbstractTask<?> setWaitingTime(int seconds) {
        this.waitingTime = seconds * 1000L;
        return this;
    }
    /**
     * 设置模式，默认模式提交任务后立即返回
     *
     * @param pattern 模式
     * @return {@link ImtpsTask }
     */
    public AbstractTask<?> setPattern(byte pattern) {
        this.pattern = pattern;
        return this;
    }

    public void setSendViewOperationObject(Object object) {
        sendView.setOperationObject(object);
    }
    public void setReceiveViewOperationObject(Object object) {
        receiveView.setOperationObject(object);
    }
    /**
     * 设置发送视图
     *
     * @param transmitView 传输视图 {@link AbstractTransmitView}
     * @return {@link ImtpsTask }
     */
    public AbstractTask<?> setSendView(AbstractTransmitView transmitView) {
        sendView = transmitView;
        return this;
    }
    /**
     * 设置接收视图
     *
     * @param transmitView 传输视图 {@link AbstractTransmitView}
     * @return {@link ImtpsTask }
     */
    public AbstractTask<?> setReceiveView(AbstractTransmitView transmitView) {
        receiveView = transmitView;
        return this;
    }
    public AbstractTransmitView getSendView() {
        return sendView;
    }
    public AbstractTransmitView getReceiveView() {
        return receiveView;
    }

    public byte getPattern() {
        return pattern;
    }
    public String getTaskId() {
        return taskId;
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

    public boolean isLive() {
        return live.get();
    }
    public void setLive(boolean live) {
        this.live.set(live);
    }

    public static String createTaskId() {
        try {
            return Tool.getStringHashValue(System.currentTimeMillis() + UUID.randomUUID().toString(), "MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
