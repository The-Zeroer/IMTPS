package com.thezeroer.imtps.client.process.handler;

import com.thezeroer.imtps.client.datapacket.DataPacket;
import com.thezeroer.imtps.client.worker.SessionManager;

/**
 * IMTPS 上下文
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/31
 */
public class ImtpsContext {
    private final SessionManager sessionManager;
    private final DataPacket requestDataPacket;

    public ImtpsContext(SessionManager sessionManager, DataPacket requestDataPacket) {
        this.sessionManager = sessionManager;
        this.requestDataPacket = requestDataPacket;
    }

    /**
     * 获取请求数据包
     *
     * @return {@link DataPacket }
     */
    public DataPacket getRequestDataPacket() {
        return requestDataPacket;
    }
    /**
     * 放置响应数据包
     *
     * @param dataPacket 数据包
     */
    public void putResponseDataPacket(DataPacket dataPacket) {
        sessionManager.putDataPacket(dataPacket.setTaskId(requestDataPacket.getTaskId()));
    }
}
