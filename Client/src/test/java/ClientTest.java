import com.thezeroer.imtps.client.IMTPS_Client;
import com.thezeroer.imtps.client.datapacket.DataPacket;
import com.thezeroer.imtps.client.datapacket.databody.FileDataBody;
import com.thezeroer.imtps.client.datapacket.databody.TextDataBody;

import java.io.File;

public class ClientTest {
    public static void main(String[] args) throws Exception {
        IMTPS_Client imtpsClient = new IMTPS_Client();
        imtpsClient.startRunning();
        imtpsClient.linkServer("127.0.0.1", 10086);
//        imtpsClient.linkServer("frp-six.com", 10754);
//        imtpsClient.sendDataPacket(DataPacket.build(0));
        imtpsClient.sendDataPacket(DataPacket.build(0).attachDataBody(new TextDataBody(".\\2.png")));
        for (int i = 0; i < 1000; i++) {
            Thread.sleep(10);
        }
//        imtpsClient.sendDataPacket(DataPacket.build(0).attachDataBody(new FileDataBody(new File(".\\2.png"))));
    }
}
