package com.thezeroer.imtps.server.buffer;

import java.nio.ByteBuffer;

/**
 * 缓冲区管理器
 *
 * @author NiZhanBo
 * @since 2025/08/18
 * @version 1.0.0
 */
public class BufferManager {
    private static final int BUFFER_SIZE = 1024 * 64;
    private static final ThreadLocal<BufferManager> threadLocal = ThreadLocal.withInitial(BufferManager::new);
    private final ByteBuffer srcBuffer, destBuffer;

    public static BufferManager get() {
        return threadLocal.get().clear();
    }

    private BufferManager() {
        srcBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        destBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    }
    private BufferManager clear() {
        srcBuffer.clear();
        destBuffer.clear();
        return this;
    }

    public ByteBuffer getSrcBuffer(int limit) {
        return srcBuffer.clear().limit(limit);
    }
    public ByteBuffer getDestBuffer(int limit) {
        return destBuffer.clear().limit(limit);
    }
}
