package imtps.client.log;

/**
 * 日志处理程序
 *
 * @author NiZhanBo
 * @since 2025/01/22
 * @version 1.0.0
 */
public interface LogHandler {
    public void handle(LogPacket logPacket);
}