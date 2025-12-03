package com.thezeroer.imtps.server.process.handler;

import com.thezeroer.imtps.server.datapacket.DataPacket;

public abstract class ProtectedHandler extends ImtpsHandler {
    /**
     * 受保护的 IMTPS 处理程序，需要会话通过验证
     *
     * @param way        指定方式，允许所有方式则null
     */
    public ProtectedHandler(DataPacket.WAY way) {
        super(true, way);
    }
}
