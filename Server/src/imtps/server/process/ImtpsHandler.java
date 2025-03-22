package imtps.server.process;

/**
 * imtp 处理程序
 *
 * @author NiZhanBo
 * @since 2025/01/25
 * @version 1.0.0
 */
public abstract class ImtpsHandler {
    private final boolean needVerify;
    public ImtpsHandler(boolean needVerify) {
        this.needVerify = needVerify;
    }
    public boolean needVerify() {
        return needVerify;
    }
    public abstract void handle(ImtpsExchange exchange);
}