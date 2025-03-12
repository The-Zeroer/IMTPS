package imtp.server.link;

import imtp.server.datapacket.DataPacket;
import imtp.server.datapacket.code.Type;
import imtp.server.datapacket.code.Way;
import imtp.server.datapacket.databody.TextDataBody;
import imtp.server.log.ImtpLogger;
import imtp.server.process.ProcessingHub;
import imtp.server.security.Secure;
import imtp.server.util.AddressManager;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * 基础连接管理器
 *
 * @author NiZhanBo
 * @since 2025/01/22
 * @version 1.0.0
 */
public class BaseLinkManager extends LinkManager {
    private final FileLinkManager fileLinkManager;
    private final AddressManager addressManager;
    public BaseLinkManager(Secure secure, LinkTable linkTable, ProcessingHub processingHub, ImtpLogger imtpLogger, FileLinkManager fileLinkManager, AddressManager addressManager) throws IOException {
        super(secure, linkTable, processingHub, imtpLogger);
        this.fileLinkManager = fileLinkManager;
        this.addressManager = addressManager;
    }

    @Override
    protected void extraCancel(SelectionKey selectionKey) {
        linkTable.cancel(selectionKey);
        fileLinkManager.cancel(selectionKey, "跟随BaseLinkManager");
    }

    @Override
    protected void tokenVerify(SelectionKey selectionKey, DataPacket dataPacket) {
        String remoteAddress = "null";
        try {
            remoteAddress = ((SocketChannel)selectionKey.channel()).getRemoteAddress().toString();
        } catch (IOException ignored) {}
        String token = (String) dataPacket.getDataBodyContent();
        SelectionKey baseSelectionKey = linkTable.getBaseSelectionKey(token);
        if (baseSelectionKey != null) {
            linkTable.updateBaseLink(baseSelectionKey, selectionKey);
        } else {
            String UID = linkTable.verifyToken(token);
            if (UID != null) {
                linkTable.register(selectionKey, UID, token);
                imtpLogger.log(ImtpLogger.LEVEL_WARN, "重新建立的连接 [$] Token验证成功，并关联UID [$]", remoteAddress, UID);
            } else {
                imtpLogger.log(ImtpLogger.LEVEL_WARN, "重新建立的连接 [$] Token验证失败", remoteAddress);
                cancel(selectionKey, "Token验证失败");
            }
        }
    }
    @Override
    protected void buildLink(SelectionKey selectionKey, DataPacket dataPacket) {
        try {
            SocketAddress address = ((SocketChannel) selectionKey.channel()).getRemoteAddress();
            switch (dataPacket.getType()) {
                case Type.FILE_LINK -> {
                    putDataPacket(selectionKey, new DataPacket(Way.BUILD_LINK, Type.FILE_LINK, new TextDataBody
                            (addressManager.getFileLinkAddress(address.toString().split("[/:]")[1]))));
                }
                case Type.WEB_LINK -> {
                    putDataPacket(selectionKey, new DataPacket(Way.BUILD_LINK, Type.WEB_LINK, new TextDataBody
                            (addressManager.getWebServerAddress(address.toString().split("[/:]")[1]))));
                }
            }
        } catch (IOException e) {
            imtpLogger.log(ImtpLogger.LEVEL_ERROR, "建立连接时出错", e);
        }
    }
}