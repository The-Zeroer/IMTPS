import com.thezeroer.imtps.client.IMTPS_Client;
import com.thezeroer.imtps.client.datapacket.DataPacket;
import com.thezeroer.imtps.client.datapacket.databody.FileDataBody;
import com.thezeroer.imtps.client.datapacket.databody.TextDataBody;
import com.thezeroer.imtps.client.process.task.ImtpsTask;

import java.io.File;

public class ClientTest {
    public static void main(String[] args) throws Exception {
        IMTPS_Client imtpsClient = new IMTPS_Client();
        imtpsClient.startRunning();
        imtpsClient.linkServer("127.0.0.1", 10086);
//        imtpsClient.linkServer("frp-six.com", 10754);
        imtpsClient.submitTask(new ImtpsTask() {
            @Override
            public DataPacket request() throws Exception {
                return DataPacket.build(DataPacket.WAY.DATA_SEND, (short) 1, (short) 2).attach(new TextDataBody("1122"));
            }

            @Override
            public void response(DataPacket dataPacket) throws Exception {
                System.out.println(dataPacket);
            }
        });
    }
}
