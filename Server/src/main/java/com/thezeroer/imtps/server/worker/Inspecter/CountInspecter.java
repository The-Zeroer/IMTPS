package com.thezeroer.imtps.server.worker.Inspecter;

import com.thezeroer.imtps.server.log.ImtpsLogger;
import com.thezeroer.imtps.server.worker.SessionManager;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 计数检查员
 *
 * @author NiZhanBo
 * @since 2025/08/13
 * @version 1.0.0
 */
public final class CountInspecter extends Inspecter {
    private final int maxCount;
    private final AtomicInteger currentCount;
    private SessionManager sessionManager;
    private long lastUpdateTime;

    public CountInspecter(int maxCount) {
        this.maxCount = maxCount;
        this.currentCount = new AtomicInteger(0);
    }

    @Override
    public boolean inspect(InetAddress inetAddress) {
        if (currentCount.get() <= maxCount) {
            currentCount.incrementAndGet();
            return true;
        } else {
            long now = System.currentTimeMillis();
            if (now - lastUpdateTime > 10000) {
                lastUpdateTime = now;
                currentCount.set(sessionManager.getSelectionKeyCount());
                inspect(inetAddress);
            } else {
                inspectLogSheet(inetAddress);
            }
            return false;
        }
    }

    @Override
    protected void log() {
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "CountInspecter拦截记录$", logSheet);
    }

    public void setSessionManager(SessionManager sessionManager) {
        if (sessionManager != null) {
            this.sessionManager = sessionManager;
        }
    }
}
