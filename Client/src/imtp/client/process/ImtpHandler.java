package imtp.client.process;

/**
 * imtp 处理程序
 *
 * @author NiZhanBo
 * @since 2025/01/25
 * @version 1.0.0
 */
public interface ImtpHandler {
    void handle(ImtpExchange exchange);
}