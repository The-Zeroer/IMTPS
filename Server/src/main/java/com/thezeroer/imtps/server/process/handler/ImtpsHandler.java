package com.thezeroer.imtps.server.process.handler;

import com.thezeroer.imtps.server.datapacket.DataPacket;
import com.thezeroer.imtps.server.process.HandlerContext;

/**
 * IMTPS 处理程序
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/31
 */
public abstract class ImtpsHandler {
    private final boolean needVerify;
    private final DataPacket.WAY way;
    private String name;

    /**
     * IMTPS 处理程序
     *
     * @param needVerify 是否需要验证，true->会话需要有会话名，false->直接通过
     * @param way 指定方式，允许所有方式则null
     */
    public ImtpsHandler(boolean needVerify, DataPacket.WAY way) {
        this.needVerify = needVerify;
        this.way = way;
    }
    /**
     * 执行
     *
     * @param handlerContext IMTPS 上下文
     */
    public abstract void execute(HandlerContext handlerContext);

    public final boolean isNeedVerify() {
        return needVerify;
    }
    public final DataPacket.WAY getWay() {
        return way;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
