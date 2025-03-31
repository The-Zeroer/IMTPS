package imtps.client;

import imtps.client.accept.HandShake;
import imtps.client.datapacket.DataBodyManager;
import imtps.client.datapacket.DataPacket;
import imtps.client.datapacket.code.Extra;
import imtps.client.datapacket.code.Type;
import imtps.client.datapacket.code.Way;
import imtps.client.datapacket.databody.AbstractDataBody;
import imtps.client.datapacket.databody.ByteDataBody;
import imtps.client.datapacket.databody.FileDataBody;
import imtps.client.datapacket.databody.TextDataBody;
import imtps.client.event.ImtpsEventCatch;
import imtps.client.link.LinkManager;
import imtps.client.link.LinkTable;
import imtps.client.log.ImtpsLogger;
import imtps.client.log.LogHandler;
import imtps.client.process.AbstractTransferSchedule;
import imtps.client.process.ImtpsHandler;
import imtps.client.process.ImtpsTask;
import imtps.client.process.ProcessingHub;
import imtps.client.security.SecureManager;
import imtps.client.util.Tool;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
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
    private final ImtpsLogger imtpsLogger;
    private final SecureManager secureManager;

    public IMTPS_Client() throws IOException, NoSuchAlgorithmException {
        secureManager = new SecureManager();
        imtpsLogger = new ImtpsLogger();
        linkTable = new LinkTable();
        processingHub = new ProcessingHub(this, imtpsLogger);
        linkManager = new LinkManager(secureManager, linkTable, processingHub, imtpsLogger);
        linkManager.setImtpsEventCatch(new ImtpsEventCatch() {});
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
    public void linkServer(String host, int port) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "正在连接服务器 [$:$]", host, port);
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(host, port));
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "已连接至服务器");
        HandShake.execute(socketChannel, secureManager, linkManager, imtpsLogger, "baseLink");
    }

    /**
     * 获取链接标志
     *
     * @return boolean 已建立连接-true，未建立连接-false
     */
    public boolean getLinkFlag() {
        return linkTable.getBaseSelectionKey() instanceof SelectionKey selectionKey && selectionKey.isValid();
    }

    /**
     * 开始运行
     */
    public void startRunning() {
        linkManager.startRunning();
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "IMTP_Client开始运行");
    }
    /**
     * 停止运行
     */
    public void stopRunning() {
        linkManager.stopRunning();
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "IMTP_Client暂停运行");
    }
    /**
     * 关闭
     */
    public void shutdown() {
        linkManager.shutdown();
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "IMTP_Client停止运行");
    }

    /**
     * 设置 连接管理器 线程池
     *
     * @param threadPool 线程池
     */
    public void setLinkManagerThreadPool(ExecutorService threadPool) {
        if (threadPool != null) {
            linkManager.setThreadPool(threadPool);
        }
    }
    /**
     * 设置处理中心线程池
     *
     * @param threadPool 线程池
     */
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
     * 设置 IMTPS 事件捕获
     *
     * @param imtpsEventCatch IMTPS 事件捕获
     */
    public void setImtpsEventCatch(ImtpsEventCatch imtpsEventCatch) {
        if (imtpsEventCatch != null) {
            linkManager.setImtpsEventCatch(imtpsEventCatch);
        }
    }

    /**
     * 设置记录日志的级别，默认记录所有日志
     *
     * @param level 低于此级别的日志不记录
     */
    public void setLoggerLevel(byte level) {
        imtpsLogger.setLevel(level);
    }
    /**
     * 设置日志处理，日志默认输出到控制台
     *
     * @param logHandler 日志处理接口
     */
    public void setLoggerHandler(LogHandler logHandler) {
        imtpsLogger.setLogHandler(logHandler);
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
     * @param imtpsTask 任务
     */
    public void submitTask(ImtpsTask imtpsTask) {
        processingHub.submitTask(imtpsTask);
    }
    /**
     * 删除任务
     *
     * @param taskId 任务 ID
     */
    public void removeTask(String taskId) {
        processingHub.removeTask(taskId);
    }

    /**
     * 提交 发送 传输时间表
     *
     * @param taskId 任务 ID
     * @param abstractTransferSchedule 摘要 传输时间表 {@link AbstractTransferSchedule}
     */
    public void submitSendTransferSchedule(String taskId, AbstractTransferSchedule abstractTransferSchedule) {
        processingHub.submitSendTransferSchedule(taskId, abstractTransferSchedule);
    }
    /**
     * 提交 接收 传输时间表
     *
     * @param taskId 任务 ID
     * @param abstractTransferSchedule 摘要 传输时间表 {@link AbstractTransferSchedule}
     */
    public void submitReceiveTransferSchedule(String taskId, AbstractTransferSchedule abstractTransferSchedule) {
        processingHub.submitReceiveTransferSchedule(taskId, abstractTransferSchedule);
    }

    /**
     * 添加处理程序
     *
     * @param way 方法
     * @param handler 处理器
     */
    public void addHandler(int way, ImtpsHandler handler) {
        processingHub.addHandler(way, Type.DEFAULT, Extra.DEFAULT, handler);
    }
    /**
     * 添加处理程序
     *
     * @param way 方法
     * @param type 类型
     * @param handler 处理器
     */
    public void addHandler(int way, int type, ImtpsHandler handler) {
        processingHub.addHandler(way, type, Extra.DEFAULT, handler);
    }
    /**
     * 添加处理程序
     *
     * @param way 方法
     * @param type 类型
     * @param extra 额外
     * @param handler 处理器
     */
    public void addHandler(int way, int type, int extra, ImtpsHandler handler) {
        processingHub.addHandler(way, type, extra, handler);
    }
    /**
     * 移除处理程序
     *
     * @param way 方法
     */
    public void removeHandler(int way) {
        processingHub.removeHandler(way, Type.DEFAULT, Extra.DEFAULT);
    }
    /**
     * 移除处理程序
     *
     * @param way 方法
     * @param type 类型
     */
    public void removeHandler(int way, int type) {
        processingHub.removeHandler(way, type, Extra.DEFAULT);
    }
    /**
     * 移除处理程序
     *
     * @param way 方法
     * @param type 类型
     * @param extra 额外
     */
    public void removeHandler(int way, int type, int extra) {
        processingHub.removeHandler(way, type, extra);
    }

    /**
     * 放入数据包，主动发送数据包
     *
     * @param dataPacket 数据包
     */
    public void putDataPacket(DataPacket dataPacket) {
        if (dataPacket == null) {
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "dataPacket为null");
            return;
        }
        if (dataPacket.baseLinkTransfer() && dataPacket.getDataBodySize() <= 1024*1024) {
            for (int i = 0; linkTable.getBaseSelectionKey() == null && i < 100; i++) {
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
    /**
     * 放入数据包，主动发送数据包
     *
     * @param dataPacket 数据包
     */
    public void putDataPacket(SelectionKey selectionKey, DataPacket dataPacket) {
        linkManager.putDataPacket(selectionKey, dataPacket);
    }
}
