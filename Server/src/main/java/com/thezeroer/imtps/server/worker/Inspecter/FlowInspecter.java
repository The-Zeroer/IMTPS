package com.thezeroer.imtps.server.worker.Inspecter;

import com.thezeroer.imtps.server.log.ImtpsLogger;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流量检查器
 *
 * @author NiZhanBo
 * @since 2025/08/13
 * @version 1.0.0
 */
public final class FlowInspecter extends Inspecter {
    private final long capacity; // 桶的容量
    private final long rate; // 令牌生成速率（每秒生成的令牌数量）
    private final AtomicLong tokens; // 当前令牌数
    private long lastTimestamp; // 上次更新时间

    public FlowInspecter(long capacity, long rate) {
        this.capacity = capacity;
        this.rate = rate;
        this.tokens = new AtomicLong(capacity);
        this.lastTimestamp = System.currentTimeMillis();
    }

    @Override
    public boolean inspect(InetAddress inetAddress) {
        if (tokens.decrementAndGet() >= 0) {
            return true;
        } else {
            synchronized (this) {
                if (tokens.get() <= 0) {
                    long now = System.currentTimeMillis();
                    long elapsedTime = now - lastTimestamp;
                    long generatedTokens = elapsedTime * rate / 1000;
                    if (generatedTokens >= 1) {
                        tokens.set(Math.min(capacity, generatedTokens));
                        lastTimestamp = now;
                        return true;
                    } else {
                        inspectLogSheet(inetAddress);
                        return false;
                    }
                } else {
                    if (tokens.decrementAndGet() >= 0) {
                        return true;
                    } else {
                        inspectLogSheet(inetAddress);
                        return false;
                    }
                }
            }
        }
    }

    @Override
    protected void log() {
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "FlowInspecter拦截记录$", logSheet);
    }
}