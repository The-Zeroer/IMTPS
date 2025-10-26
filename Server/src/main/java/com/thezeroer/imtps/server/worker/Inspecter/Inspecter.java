package com.thezeroer.imtps.server.worker.Inspecter;

import com.thezeroer.imtps.server.log.ImtpsLogger;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 检查员
 *
 * @author NiZhanBo
 * @since 2025/08/13
 * @version 1.0.0
 */
public abstract class Inspecter {
    protected static final int LOG_BATCH_THRESHOLD = 50000;
    protected final AtomicInteger atomicInteger = new AtomicInteger(0);
    protected ConcurrentHashMap<InetAddress, AtomicInteger> logSheet = new ConcurrentHashMap<>();
    protected ImtpsLogger imtpsLogger;
    private final AtomicBoolean enable = new AtomicBoolean(false);

    public abstract boolean inspect(InetAddress inetAddress);
    protected void setLogger(ImtpsLogger imtpsLogger) {
        this.imtpsLogger = imtpsLogger;
    }
    protected void clearLogSheet() {
        if (!logSheet.isEmpty()) {
            log();
        }
        logSheet = new ConcurrentHashMap<>();
        atomicInteger.set(0);
    }
    protected void inspectLogSheet(InetAddress inetAddress) {
        if (atomicInteger.incrementAndGet() <= LOG_BATCH_THRESHOLD) {
            logSheet.computeIfAbsent(inetAddress, (k) -> new AtomicInteger(0)).incrementAndGet();
        } else {
            synchronized (this) {
                if (atomicInteger.get() > LOG_BATCH_THRESHOLD) {
                    log();
                    logSheet = new ConcurrentHashMap<>();
                    atomicInteger.set(0);
                }
            }
        }
    }
    protected void log() {}

    protected boolean isEnable() {
        return enable.get();
    }
    protected void setEnable(boolean enable) {
        this.enable.set(enable);
    }
}
