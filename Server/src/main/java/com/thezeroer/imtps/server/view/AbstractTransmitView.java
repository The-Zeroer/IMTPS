package com.thezeroer.imtps.server.view;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 抽象传输视图
 *
 * @author NiZhanBo
 * @since 2025/08/19
 * @version 1.0.0
 */
public abstract class AbstractTransmitView implements Runnable{
    protected Object operationObject;
    protected Byte currentPacketIndex;
    protected long sumSize, currentSize;
    protected byte[] meta;
    private final AtomicBoolean submitted = new AtomicBoolean(false);

    protected AbstractTransmitView() {}

    public AbstractTransmitView setOperationObject(Object operationObject) {
        this.operationObject = operationObject;
        return this;
    }

    public abstract void begin();
    public abstract void run();
    public abstract void finish();
    public void close() {}

    public double getProgress() {
        if (sumSize <= 0) return 0;
        return Math.min((double) currentSize / sumSize, 1.0);
    }

    public final boolean trySubmit() {
        return submitted.compareAndSet(false, true);
    }
    public final void setInfo(byte currentPacketIndex, long sumSize, byte[] meta) {
        this.currentPacketIndex = currentPacketIndex;
        this.sumSize = sumSize;
        this.meta = meta;
        currentSize = 0;
    }
    public final void updateSize(long size) {
        currentSize += size;
    }
}
