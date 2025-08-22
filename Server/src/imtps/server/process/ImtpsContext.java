package imtps.server.process;

import imtps.server.datapacket.DataPacket;
import imtps.server.session.ImtpsSession;
import imtps.server.worker.SessionManager;

/**
 * IMTPS 上下文
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/31
 */
public class ImtpsContext {
    private final SessionManager sessionManager;
    private final ImtpsSession imtpsSession;
    private final DataPacket requestDataPacket;

    protected ImtpsContext(SessionManager sessionManager, ImtpsSession imtpsSession, DataPacket requestDataPacket) {
        this.sessionManager = sessionManager;
        this.imtpsSession = imtpsSession;
        this.requestDataPacket = requestDataPacket;
    }

    /**
     * 获取请求数据包
     *
     * @return {@link DataPacket }
     */
    public DataPacket getRequestDataPacket() {
        return requestDataPacket;
    }
    /**
     * 放置响应数据包
     *
     * @param dataPacket 数据包
     */
    public void putResponseDataPacket(DataPacket dataPacket) {
        sessionManager.putDataPacket(imtpsSession, dataPacket.setTaskId(requestDataPacket.getTaskId()));
    }

    /**
     * 设置会话名称
     *
     * @param sessionName 会话名称
     */
    public void setSessionName(String sessionName) {
        imtpsSession.setSessionName(sessionName);
        sessionManager.getNameToSessionHashMap().put(sessionName, imtpsSession);
    }
    /**
     * 获取会话名称
     *
     * @return {@link String }
     */
    public String getSessionName() {
        return imtpsSession.getSessionName();
    }

}
