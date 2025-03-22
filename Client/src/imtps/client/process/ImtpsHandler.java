package imtps.client.process;

/**
 * imtp 处理程序
 *
 * @author NiZhanBo
 * @since 2025/01/25
 * @version 1.0.0
 */
public interface ImtpsHandler {
    void handle(ImtpsExchange exchange);
}