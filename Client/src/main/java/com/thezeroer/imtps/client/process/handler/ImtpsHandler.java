package com.thezeroer.imtps.client.process.handler;

/**
 * IMTPS 处理程序
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/31
 */
public abstract class ImtpsHandler {
    /**
     * IMTPS 处理程序
     */
    public ImtpsHandler() {}
    /**
     * 执行
     *
     * @param imtpsContext IMTPS 上下文
     */
    public abstract void execute(ImtpsContext imtpsContext);

    /**
     * 获取方式匹配，null为通配符
     *
     * @return {@link int[] }
     */
    public abstract Object getWayMatch();
    /**
     * 获取类型匹配，null为通配符
     *
     * @return {@link int[] }
     */
    public abstract Object getTypeMach();
    /**
     * 获得额外匹配，null为通配符
     *
     * @return {@link int[] }
     */
    public abstract Object getExtraMatch();
}
