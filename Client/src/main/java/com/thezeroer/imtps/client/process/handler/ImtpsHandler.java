package com.thezeroer.imtps.client.process.handler;

import com.thezeroer.imtps.client.datapacket.DataPacket;

/**
 * IMTPS 处理程序
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/31
 */
public abstract class ImtpsHandler {
    private final DataPacket.WAY way;
    private String name;

    /**
     * IMTPS 处理程序
     *
     * @param way 指定方式，允许所有方式则null
     */
    public ImtpsHandler(DataPacket.WAY way) {
        this.way = way;
    }
    /**
     * 执行
     *
     * @param handlerContext IMTPS 上下文
     */
    public abstract void execute(HandlerContext handlerContext);

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
