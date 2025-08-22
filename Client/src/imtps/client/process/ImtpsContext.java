package imtps.client.process;

import imtps.client.datapacket.DataPacket;
import imtps.client.worker.SessionManager;

/**
 * IMTPS 上下文
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/31
 */
public class ImtpsContext {
    private final SessionManager sessionManager;
    private final DataPacket requestDataPacket;

    public ImtpsContext(SessionManager sessionManager, DataPacket requestDataPacket) {
        this.sessionManager = sessionManager;
        this.requestDataPacket = requestDataPacket;
    }

    public DataPacket getRequestDataPacket() {
        return requestDataPacket;
    }
    public void putResponseDataPacket(DataPacket dataPacket) {
        sessionManager.putDataPacket(dataPacket.setTaskId(requestDataPacket.getTaskId()));
    }

}
