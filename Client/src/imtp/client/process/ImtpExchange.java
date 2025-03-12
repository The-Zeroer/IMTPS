package imtp.client.process;

import imtp.client.IMTPS_Client;
import imtp.client.datapacket.DataPacket;

public class ImtpExchange {
    private final IMTPS_Client imtpClient;
    private final DataPacket requestDataPacket;

    public ImtpExchange(IMTPS_Client imtpClient, DataPacket dataPacket) {
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