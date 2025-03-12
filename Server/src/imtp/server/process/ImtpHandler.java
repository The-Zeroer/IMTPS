package imtp.server.process;

/**
 * imtp 处理程序
 *
 * @author NiZhanBo
 * @since 2025/01/25
 * @version 1.0.0
 */
public abstract class ImtpHandler {
    private final boolean needVerify;
    public ImtpHandler(boolean needVerify) {
        this.needVerify = needVerify;
    }
    public boolean needVerify() {
        return needVerify;
    }
    public abstract void handle(ImtpExchange exchange);
}