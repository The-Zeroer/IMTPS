package com.thezeroer.imtps.server.view;

/**
 * 抽象传输视图
 *
 * @author NiZhanBo
 * @since 2025/08/19
 * @version 1.0.0
 */
public abstract class AbstractTransmitView extends Thread{
    protected long sumSize, currentSize;
    protected byte[] metadata;

    protected AbstractTransmitView() {}

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
}
