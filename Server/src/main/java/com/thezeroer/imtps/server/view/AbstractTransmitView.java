package com.thezeroer.imtps.server.view;

/**
 * 抽象传输视图
 *
 * @author NiZhanBo
 * @since 2025/08/19
 * @version 1.0.0
 */
public abstract class AbstractTransmitView implements Runnable{
    protected Object operationObject;
    protected long sumSize, currentSize;
    protected byte[] meta;

    protected AbstractTransmitView() {}

    public AbstractTransmitView setOperationObject(Object operationObject) {
        this.operationObject = operationObject;
        return this;
    }

    public abstract void begin();
    public abstract void run();
    public abstract void finish();

    public double getProgress() {
        if (sumSize <= 0) return 0;
        return Math.min((double) currentSize / sumSize, 1.0);
    }

    public final void setInfo(long sumSize, byte[] meta) {
        this.sumSize = sumSize;
        this.meta = meta;
        currentSize = 0;
    }
    public final void updateSize(long size) {
        currentSize += size;
    }
}
