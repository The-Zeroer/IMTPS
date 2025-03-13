package imtp.server.link;

import imtp.server.datapacket.DataPacket;
import imtp.server.datapacket.databody.AbstractDataBody;
import imtp.server.log.ImtpLogger;
import imtp.server.process.ProcessingHub;
import imtp.server.security.Secure;
import imtp.server.util.Tool;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * 文件连接管理器
 *
 * @author NiZhanBo
 * @since 2025/01/22
 * @version 1.0.0
 */
public class FileLinkManager extends LinkManager {
    public FileLinkManager(Secure secure, LinkTable linkTable, ProcessingHub processingHub, ImtpLogger imtpLogger) throws IOException {
        super(secure, linkTable, processingHub, imtpLogger);
    }

    @Override
    protected void extraCancel(SelectionKey selectionKey) {
        linkTable.unbindFileSelectionKey(selectionKey);
    }

    @Override
    protected void tokenVerify(SelectionKey selectionKey, DataPacket dataPacket) {
        if (dataPacket.getDataBody() instanceof AbstractDataBody<?> dataBody) {
            try {
                SocketAddress socketAddress = ((SocketChannel) selectionKey.channel()).getRemoteAddress();
                String token = (String) dataBody.getContent();
                SelectionKey baseSelectionKey = linkTable.getBaseSelectionKey(token);
                if (baseSelectionKey == null) {
                    cancel(selectionKey, "Token验证失败");
                    return;
                }
                SelectionKey fileSelectionKey = linkTable.getFileSelectionKey(token);
                if (fileSelectionKey != null) {
                    SocketAddress oldSocketAddress = ((SocketChannel) fileSelectionKey.channel()).getRemoteAddress();
                    cancel(fileSelectionKey, "FileLink替换");
                    imtpLogger.log(ImtpLogger.LEVEL_WARN, "连接(File) [$] 替换为 [$]", oldSocketAddress, socketAddress);
                }
                String UID = linkTable.bindFileSelectionKey(baseSelectionKey, selectionKey);
                imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "连接(File) [$] Token验证通过, 并绑定UID [$]", socketAddress, UID);
                for (int i = 0; selectionKey.attachment() == null && i < 100; i++) {
                    Tool.sleep();
                }
                for (DataPacket cacheDataPacket : linkTable.getCacheDataPacketQueue(UID)) {
                    putDataPacket(selectionKey, cacheDataPacket);
                }
                linkTable.removeCacheDataPacketQueue(UID);
                linkTable.setFileLinkState(UID, LinkTable.LINKSTATE_READY);
            } catch (IOException ignored) {}
        }
    }
}