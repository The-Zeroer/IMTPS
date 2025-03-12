package imtp.server.process;

import imtp.server.IMTPS_Server;
import imtp.server.datapacket.DataPacket;

public class ImtpExchange {
    private final IMTPS_Server imtpServer;
    private final DataPacket requestDataPacket;

    public ImtpExchange(IMTPS_Server imtpServer, DataPacket dataPacket) {
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