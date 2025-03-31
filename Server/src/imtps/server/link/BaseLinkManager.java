package imtps.server.link;

import imtps.server.datapacket.DataPacket;
import imtps.server.datapacket.code.Type;
import imtps.server.datapacket.code.Way;
import imtps.server.datapacket.databody.TextDataBody;
import imtps.server.log.ImtpsLogger;
import imtps.server.process.ProcessingHub;
import imtps.server.security.SecureManager;
import imtps.server.util.AddressManager;

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
    public BaseLinkManager(SecureManager secureManager, LinkTable linkTable, ProcessingHub processingHub, ImtpsLogger imtpsLogger, FileLinkManager fileLinkManager, AddressManager addressManager) throws IOException {
        super(secureManager, linkTable, processingHub, imtpsLogger);
        this.fileLinkManager = fileLinkManager;
        this.addressManager = addressManager;
    }

    @Override
    protected void extraCancel(SelectionKey selectionKey) {
        linkTable.cancel(selectionKey);
        fileLinkManager.cancel(selectionKey, false, "跟随BaseLinkManager");
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
                imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "重新建立的连接 [$] Token验证成功，并关联UID [$]", remoteAddress, UID);
            } else {
                imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "重新建立的连接 [$] Token验证失败", remoteAddress);
                cancel(selectionKey, false, "Token验证失败");
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
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "建立连接时出错", e);
        }
    }
}