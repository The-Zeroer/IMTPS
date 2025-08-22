package imtps.server.process;

/**
 * IMTPS 处理程序
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/31
 */
public abstract class ImtpsHandler {
    private final boolean needVerify;
    public ImtpsHandler(boolean needVerify) {
        this.needVerify = needVerify;
    }
    public abstract void execute(ImtpsContext imtpsContext);
    public final boolean isNeedVerify() {
        return needVerify;
    }
}
