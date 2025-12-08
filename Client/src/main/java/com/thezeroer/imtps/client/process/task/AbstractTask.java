package com.thezeroer.imtps.client.process.task;

import com.thezeroer.imtps.client.util.Tool;

import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * 抽象任务
 *
 * @author NiZhanBo
 * @since 2025/09/24
 * @version 1.0.0
 */
public abstract sealed class AbstractTask<T> permits ImtpsTask, ImtpsTaskList, ImtpsTaskMap, ImtpsTaskSet {
    /** 默认模式<br>提交任务后立即返回 */
    public static final byte PATTERN_DEFAULT = 0;
    /** 队列模式<br>如果此时没有等待响应的任务，提交此任务后立即返回<br>否则加入请求队列后立即返回，等待响应后请求 */
    public static final byte PATTERN_QUEUE = 1;
    /** 等待模式<br>提交任务后等待数据 */
    public static final byte PATTERN_WAIT = 2;

    private final String taskId;
    private long waitingTime;
    private byte pattern = PATTERN_DEFAULT;

    public AbstractTask() {
        this(createTaskId());
    }
    public AbstractTask(String taskId) {
        this.taskId = taskId;
        waitingTime = 10000L;
    }

    public abstract T request() throws Exception;
    public abstract void response(T t) throws Exception;
    public void done(boolean normal) {}

    /**
     * 设置等待时间，默认10秒
     *
     * @param seconds 秒
     * @return {@link ImtpsTask }
     */
    public AbstractTask<T> setWaitingTime(int seconds) {
        this.waitingTime = seconds * 1000L;
        return this;
    }
    /**
     * 设置模式，默认模式提交任务后立即返回
     *
     * @param pattern 模式
     * @return {@link ImtpsTask }
     */
    public AbstractTask<T> setPattern(byte pattern) {
        this.pattern = pattern;
        return this;
    }

    public final byte getPattern() {
        return pattern;
    }
    public final String getTaskId() {
        return taskId;
    }
    public final long getWaitingTime() {
        return waitingTime;
    }

    public static String createTaskId() {
        try {
            return Tool.getStringHashValue(UUID.randomUUID().toString(), "MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
