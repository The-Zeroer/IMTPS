import com.thezeroer.imtps.server.IMTPS_Server;
import com.thezeroer.imtps.server.process.handler.ImtpsContext;
import com.thezeroer.imtps.server.process.handler.ImtpsHandler;
import com.thezeroer.imtps.server.session.channel.ImtpsChannel;

import java.io.IOException;

public class ServerTest {
    public static void main(String[] args) throws IOException {
        IMTPS_Server imtpsServer = new IMTPS_Server();
        imtpsServer.bindAllPort(10086, 0, 0);
        imtpsServer.registerHandler(new ImtpsHandler(false) {
            @Override
            public void execute(ImtpsContext imtpsContext) {

            }

            @Override
            public Object getWayMatch() {
                return null;
            }

            @Override
            public Object getTypeMach() {
                return null;
            }

            @Override
            public Object getExtraMatch() {
                return null;
            }
        });
        imtpsServer.setSessionHeartBeatInterval(ImtpsChannel.TYPE.Control, 100000);
        imtpsServer.setSessionHeartBeatInterval(ImtpsChannel.TYPE.DataFile, 100000);
        imtpsServer.startRunning();
    }
}
