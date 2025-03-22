package imtps.server.process;

import imtps.server.IMTPS_Server;
import imtps.server.datapacket.DataPacket;

public class ImtpsExchange {
    private final IMTPS_Server imtpServer;
    private final DataPacket requestDataPacket;

    public ImtpsExchange(IMTPS_Server imtpServer, DataPacket dataPacket) {
        this.imtpServer = imtpServer;
        requestDataPacket = dataPacket;
    }

    public DataPacket getRequestDataPacket() {
        return requestDataPacket;
    }

    public void putResponseDataPacket(DataPacket dataPacket) {
        imtpServer.putDataPacket(requestDataPacket.getSelectionKey(), dataPacket.setTaskId(requestDataPacket.getTaskId()));
    }
}