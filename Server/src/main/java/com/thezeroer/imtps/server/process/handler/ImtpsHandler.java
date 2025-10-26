package com.thezeroer.imtps.server.process.handler;

/**
 * IMTPS 处理程序
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/31
 */
public abstract class ImtpsHandler {
    private final boolean needVerify;
    /**
     * IMTPS 处理程序
     *
     * @param needVerify 是否需要验证，true->会话需要有会话名，false->直接通过
     */
    public ImtpsHandler(boolean needVerify) {
        this.needVerify = needVerify;
    }
    /**
     * 执行
     *
     * @param imtpsContext IMTPS 上下文
     */
    public abstract void execute(ImtpsContext imtpsContext);

    /**
     * 获取方式匹配，null为通配符，多个匹配项使用int[]
     *
     * @return {@link int[] }
     */
    public abstract Object getWayMatch();
    /**
     * 获取类型匹配，null为通配符，多个匹配项使用int[]
     *
     * @return {@link int[] }
     */
    public abstract Object getTypeMach();
    /**
     * 获得额外匹配，null为通配符，多个匹配项使用int[]
     *
     * @return {@link int[] }
     */
    public abstract Object getExtraMatch();

    public final boolean isNeedVerify() {
        return needVerify;
    }
}
