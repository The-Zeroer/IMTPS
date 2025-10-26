package com.thezeroer.imtps.client.event;

/**
 * IMTPS 事件捕获
 *
 * @author NiZhanBo
 * @since 2025/08/12
 * @version 1.0.0
 */
public interface ImtpsEventCatch {
    /** 服务器主动关闭连接 */
    default void serverClose(boolean initiative) {}
    /** 接收数据时连接中断 */
    default void readBreak() {}
    /** 发送数据时连接中断 */
    default void writeBreak() {}
    /**
     * 重新连接服务器
     *
     * @param succeed 成功-true，失败-false
     */
    default void reconnection(boolean succeed) {}
}
