package com.thezeroer.imtps.server.process.handler;

import com.thezeroer.imtps.server.datapacket.DataPacket;

public abstract class PublicHandler extends ImtpsHandler{
    /**
     * 公共的 IMTPS 处理程序，不需要会话通过验证
     *
     * @param way 指定方式，允许所有方式则null
     */
    public PublicHandler(DataPacket.WAY way) {
        super(false, way);
    }
}
