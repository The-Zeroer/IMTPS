package imtp.client;

import imtp.client.datapacket.DataBodyManager;
import imtp.client.datapacket.DataPacket;
import imtp.client.datapacket.code.Extra;
import imtp.client.datapacket.code.Type;
import imtp.client.datapacket.code.Way;
import imtp.client.datapacket.databody.AbstractDataBody;
import imtp.client.datapacket.databody.ByteDataBody;
import imtp.client.datapacket.databody.FileDataBody;
import imtp.client.datapacket.databody.TextDataBody;
import imtp.client.link.LinkManager;
import imtp.client.link.LinkTable;
import imtp.client.log.LogHandler;
import imtp.client.log.ImtpLogger;
import imtp.client.process.ImtpHandler;
import imtp.client.process.ImtpTask;
import imtp.client.process.ProcessingHub;
import imtp.client.process.TransferSchedule;
import imtp.client.security.Secure;
import imtp.client.util.Tool;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;

/**
 * 即时传输协议客户端
 *
 * @author NiZhanBo
 * @since 2025/01/22
 * @version 1.0.0
 */
public class IMTPS_Client {
    private final LinkManager linkManager;
    private final LinkTable linkTable;
    private final ProcessingHub processingHub;
    private final ImtpLogger imtpLogger;
    private final Secure secure;

    public IMTPS_Client() throws IOException, NoSuchAlgorithmException {
        secure = new Secure();
        imtpLogger = new ImtpLogger();
        linkTable = new LinkTable();
        processingHub = new ProcessingHub(this, imtpLogger);
        linkManager = new LinkManager(secure, linkTable, processingHub, imtpLogger);
        DataBodyManager.addDataBody(new ByteDataBody());
        DataBodyManager.addDataBody(new TextDataBody());
        DataBodyManager.addDataBody(new FileDataBody());
    }

    /**
     * 连接服务器
     *
     * @param host 主机
     * @param port 端口
     */
    public void linkServer(String host, int port) throws IOException {
        imtpLogger.log(ImtpLogger.LEVEL_INFO, "正在连接服务器 [$:$]", host, port);
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(host, port));
        imtpLogger.log(ImtpLogger.LEVEL_INFO, "已连接至服务器");
        linkManager.register(socketChannel, "baseLink");
    }

    /**
     * 开始运行
     */
    public void startRunning() {
        linkManager.startRunning();
        imtpLogger.log(ImtpLogger.LEVEL_INFO, "IMTP_Client开始运行");
    }
    /**
     * 停止运行
     */
    public void stopRunning() {
        linkManager.stopRunning();
        imtpLogger.log(ImtpLogger.LEVEL_INFO, "IMTP_Client暂停运行");
    }
    /**
     * 关闭
     */
    public void shutdown() {
        linkManager.shutdown();
        imtpLogger.log(ImtpLogger.LEVEL_INFO, "IMTP_Client停止运行");
    }

    /**
     * 设置 LinkManager 线程池
     *
     * @param threadPool 线程池
     */
    public void setLinkManagerThreadPool(ExecutorService threadPool) {
        if (threadPool != null) {
            linkManager.setThreadPool(threadPool);
        }
    }
    public void setProcessingHubThreadPool(ExecutorService threadPool) {
        if (threadPool != null) {
            processingHub.setThreadPool(threadPool);
        }
    }
    /**
     * 设置 LinkManager 心率间隔，单位秒
     *
     * @param interval 间隔
     */
    public void setLinkManagerHeartBeatInterval(int interval) {
        if (interval > 0) {
            linkManager.setHeartBeatInterval(interval);
        }
    }

    /**
     * 设置记录日志的级别，默认记录所有日志
     *
     * @param level 低于此级别的日志不记录
     */
    public void setLoggerLevel(byte level) {
        imtpLogger.setLevel(level);
    }
    /**
     * 设置日志处理，日志默认输出到控制台
     *
     * @param logHandler 日志处理接口
     */
    public void setLoggerHandler(LogHandler logHandler) {
        imtpLogger.setLogHandler(logHandler);
    }

    /**
     * 添加数据主体类
     *
     * @param dataBody 数据体 <br>
     * {@link AbstractDataBody}
     */
    public boolean addDataBodyClass(AbstractDataBody<?> dataBody) {
        return DataBodyManager.addDataBody(dataBody);
    }
    /**
     * 删除数据主体类
     *
     * @param dataBody 数据体 <br>
     * {@link AbstractDataBody}
     */
    public boolean removeDataBodyClass(AbstractDataBody<?> dataBody) {
        return DataBodyManager.removeDataBody(dataBody);
    }

    /**
     * 设置文件缓存路径，默认路径为".\ImtpFileCache\"，创建失败时路径为".\"
     *
     * @param fileCachePath 文件缓存路径
     * @throws FileNotFoundException 无法创建该路径
     */
    public void setFileCachePath(String fileCachePath) throws FileNotFoundException {
        FileDataBody.setFileCachePath(fileCachePath);
    }

    /**
     * 提交任务
     *
     * @param imtpTask 任务
     */
    public void submitTask(ImtpTask imtpTask) {
        processingHub.submitTask(imtpTask);
    }
    /**
     * 删除任务
     *
     * @param taskId 任务 ID
     */
    public void removeTask(String taskId) {
        processingHub.removeTask(taskId);
    }
    public void submitSendTransferSchedule(String taskId, TransferSchedule transferSchedule) {
        processingHub.submitSendTransferSchedule(taskId, transferSchedule);
    }
    public void submitReceiveTransferSchedule(String taskId, TransferSchedule transferSchedule) {
        processingHub.submitReceiveTransferSchedule(taskId, transferSchedule);
    }
    public void addHandler(int way, ImtpHandler handler) {
        processingHub.addHandler(way, Type.DEFAULT, Extra.DEFAULT, handler);
    }
    public void addHandler(int way, int type, ImtpHandler handler) {
        processingHub.addHandler(way, type, Extra.DEFAULT, handler);
    }
    public void addHandler(int way, int type, int extra, ImtpHandler handler) {
        processingHub.addHandler(way, type, extra, handler);
    }
    public void removeHandler(int way) {
        processingHub.removeHandler(way, Type.DEFAULT, Extra.DEFAULT);
    }
    public void removeHandler(int way, int type) {
        processingHub.removeHandler(way, type, Extra.DEFAULT);
    }
    public void removeHandler(int way, int type, int extra) {
        processingHub.removeHandler(way, type, extra);
    }

    public void putDataPacket(DataPacket dataPacket) {
        if (dataPacket == null) {
            imtpLogger.log(ImtpLogger.LEVEL_ERROR, "dataPacket为null");
            return;
        }
        if (dataPacket.baseLinkTransfer() && dataPacket.getDataBodySize() <= 1024*1024) {
            for (int i = 0;!(linkTable.getBaseSelectionKey() instanceof SelectionKey baseSelectionKey && baseSelectionKey.attachment() != null) && i < 100; i++) {
                Tool.sleep();
            }
            linkManager.putDataPacket(linkTable.getBaseSelectionKey(), dataPacket);
        } else {
            switch (linkTable.getFileLinkState()) {
                case LinkTable.LINKSTATE_READY -> {
                    linkManager.putDataPacket(linkTable.getFileSelectionKey(), dataPacket);
                }
                case LinkTable.LINKSTATE_Linked, LinkTable.LINKSTATE_Linking -> {
                    linkTable.getCacheDataPacketQueue().add(dataPacket);
                }
                case LinkTable.LINKSTATE_CLOSED -> {
                    for (int i = 0;linkTable.getBaseSelectionKey() == null && i < 100; i++) {
                        Tool.sleep();
                    }
                    if (linkManager.putDataPacket(linkTable.getBaseSelectionKey(), new DataPacket(Way.BUILD_LINK, Type.FILE_LINK))) {
                        linkTable.setFileLinkState(LinkTable.LINKSTATE_Linking);
                        linkTable.getCacheDataPacketQueue().add(dataPacket);
                    }
                }
            }
        }
    }
    public void putDataPacket(SelectionKey selectionKey, DataPacket dataPacket) {
        linkManager.putDataPacket(selectionKey, dataPacket);
    }
}