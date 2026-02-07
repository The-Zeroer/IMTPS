import com.thezeroer.imtps.server.IMTPS_Server;
import com.thezeroer.imtps.server.datapacket.DataPacket;
import com.thezeroer.imtps.server.datapacket.databody.FileDataBody;
import com.thezeroer.imtps.server.datapacket.databody.TextDataBody;
import com.thezeroer.imtps.server.process.HandlerRegistry;
import com.thezeroer.imtps.server.process.handler.HandlerContext;
import com.thezeroer.imtps.server.process.handler.PublicHandler;
import com.thezeroer.imtps.server.session.channel.ImtpsChannel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ServerTest {
    public static void main(String[] args) throws IOException {
        IMTPS_Server imtpsServer = new IMTPS_Server();
        imtpsServer.bindAllPort(7709, 7710, 7711);
        imtpsServer.registerHandler(new HandlerRegistry.PathsMatcher((short) 1, (short) 2), new PublicHandler(null) {
            @Override
            public void execute(HandlerContext handlerContext) {
                try {
                    handlerContext.putResponseDataPacket(DataPacket.build(DataPacket.WAY.RESPONSE_Ok).attach(
                            new TextDataBody("Hello World!"), new FileDataBody(new File("D:\\TBRTZ\\Downloads\\Edge\\EVE空间站_3.4.1ω_x64.zip"))));
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        imtpsServer.setSessionHeartBeatInterval(ImtpsChannel.TYPE.Control, 100000);
        imtpsServer.setSessionHeartBeatInterval(ImtpsChannel.TYPE.DataFile, 100000);
        imtpsServer.startRunning();

//        HandlerRegistry<String> handlerRegistry = new HandlerRegistry<>();
//        handlerRegistry.register(new HandlerRegistry.PathsMatcher(), "1-2");
//        System.out.println(handlerRegistry.match(new short[]{}));

//        DataPacket dataPacket = DataPacket.build(DataPacket.WAY.DATA_REQUEST, (short) 1, (short) 2).attach(new TextDataBody("112"));
//        System.out.println(dataPacket);
    }
}
