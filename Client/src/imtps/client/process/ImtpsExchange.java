package imtps.client.process;

import imtps.client.IMTPS_Client;
import imtps.client.datapacket.DataPacket;

public class ImtpsExchange {
    private final IMTPS_Client imtpClient;
    private final DataPacket requestDataPacket;

    public ImtpsExchange(IMTPS_Client imtpClient, DataPacket dataPacket) {
        this.imtpClient = imtpClient;
        requestDataPacket = dataPacket;
    }

    public DataPacket getRequestDataPacket() {
        return requestDataPacket;
    }

    public void putResponseDataPacket(DataPacket dataPacket) {
        imtpClient.putDataPacket(requestDataPacket.getSelectionKey(), dataPacket);
    }
}