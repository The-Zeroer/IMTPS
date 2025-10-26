package com.thezeroer.imtps.client.view;

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
    protected byte[] metadata;

    protected AbstractTransmitView() {}

    public void setOperationObject(Object operationObject) {
        this.operationObject = operationObject;
    }

    public final void setMetadata(byte[] metadata) {
        this.metadata = metadata;
    }
    public final void setSumSize(long sumSize) {
        this.sumSize = sumSize;
    }
    public final void updateSize(long size) {
        currentSize += size;
    }

    public abstract void begin();
    public abstract void run();
    public abstract void finish();

    public double getProgress() {
        if (sumSize <= 0) return 0;
        return Math.min((double) currentSize / sumSize, 1.0);
    }
}
