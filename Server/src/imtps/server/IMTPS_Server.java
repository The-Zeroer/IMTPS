package imtps.server;

import imtps.server.datapacket.DataBodyManager;
import imtps.server.datapacket.DataPacket;
import imtps.server.datapacket.code.Extra;
import imtps.server.datapacket.code.Type;
import imtps.server.datapacket.code.Way;
import imtps.server.datapacket.databody.AbstractDataBody;
import imtps.server.datapacket.databody.ByteDataBody;
import imtps.server.datapacket.databody.FileDataBody;
import imtps.server.datapacket.databody.TextDataBody;
import imtps.server.link.BaseLinkManager;
import imtps.server.link.FileLinkManager;
import imtps.server.link.LinkTable;
import imtps.server.accept.Listeners;
import imtps.server.log.ImtpsLogger;
import imtps.server.log.LogHandler;
import imtps.server.process.ImtpsHandler;
import imtps.server.process.ImtpsTask;
import imtps.server.process.ProcessingHub;
import imtps.server.process.AbstractTransferSchedule;
import imtps.server.security.SecureManager;
import imtps.server.util.AddressManager;
import imtps.server.util.NetFilter;
import imtps.server.util.TokenBucket;
import imtps.server.util.Tool;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 即时传输协议服务器
 *
 * @author NiZhanBo
 * @since 2025/01/23
 * @version 1.0.0
 */
public class IMTPS_Server {
    private final Listeners listeners;
    private final BaseLinkManager baseLinkManager;
    private final FileLinkManager fileLinkManager;
    private final ProcessingHub processingHub;
    private final AddressManager addressManager;
    private final LinkTable linkTable;
    private final ImtpsLogger imtpsLogger;
    private final SecureManager secureManager;

    public IMTPS_Server() throws IOException, NoSuchAlgorithmException {
        secureManager = new SecureManager();
        imtpsLogger = new ImtpsLogger();
        linkTable = new LinkTable(imtpsLogger);
        addressManager = new AddressManager();
        processingHub = new ProcessingHub(this, imtpsLogger);
        fileLinkManager = new FileLinkManager(secureManager, linkTable, processingHub, imtpsLogger);
        baseLinkManager = new BaseLinkManager(secureManager, linkTable, processingHub, imtpsLogger, fileLinkManager, addressManager);
        listeners = new Listeners(secureManager, baseLinkManager, fileLinkManager, imtpsLogger);
        DataBodyManager.addDataBody(new ByteDataBody());
        DataBodyManager.addDataBody(new TextDataBody());
        DataBodyManager.addDataBody(new FileDataBody());
    }

    /**
     * 绑定基本端口
     *
     * @param port 需要绑定的端口，”0“为不指定端口
     */
    public void bindBasePort(int port) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        try {
            serverSocketChannel.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "基本端口冲突 [$] ", port);
            serverSocketChannel.close();
            throw e;
        }
        listeners.setBaseServerSocketChannel(serverSocketChannel);
        int binPort = serverSocketChannel.socket().getLocalPort();
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "基本端口已绑定 [$] ", binPort);
        addressManager.setBasePort(binPort);
    }
    /**
     * 绑定文件端口
     *
     * @param port 需要绑定的端口，”0“为不指定端口
     */
    public void bindFilePort(int port) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        try {
            serverSocketChannel.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "文件端口冲突 [$] ", port);
            serverSocketChannel.close();
            throw e;
        }
        listeners.setFileServerSocketChannel(serverSocketChannel);
        int binPort = serverSocketChannel.socket().getLocalPort();
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "文件端口已绑定 [$] ", binPort);
        addressManager.setLanFileLinkAddress(InetAddress.getLocalHost().getHostAddress() + ":" + binPort);
        addressManager.setFilePort(binPort);
    }

    /**
     * 开始监听端口，接受新连接
     */
    public void startRunning() throws IOException {
        if (listeners.getBaseServerSocketChannel() == null) {
            bindBasePort(0);
        }
        if (listeners.getFileServerSocketChannel() == null) {
            bindFilePort(0);
        }
        fileLinkManager.startRunning();
        baseLinkManager.startRunning();
        listeners.startRunning();
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "IMTP_Server开始运行，本机IP [$]", InetAddress.getLocalHost().getHostAddress());
    }
    /**
     * 停止监听端口，不接受新的连接，已建立的连接暂停服务
     */
    public void stopRunning() {
        listeners.stopRunning();
        baseLinkManager.stopRunning();
        fileLinkManager.stopRunning();
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "IMTP_Server暂停运行");
    }
    /**
     * 停止监听端口，并关闭已建立的连接
     */
    public void shutdown() {
        listeners.shutdown();
        baseLinkManager.shutdown();
        fileLinkManager.shutdown();
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "IMTP_Server停止运行");
    }

    public void setBaseLinkManagerThreadPool(ExecutorService threadPool) {
        if (threadPool != null) {
            baseLinkManager.setThreadPool(threadPool);
        }
    }
    public void setFileLinkManagerThreadPool(ExecutorService threadPool) {
        if (threadPool != null) {
            fileLinkManager.setThreadPool(threadPool);
        }
    }
    public void setProcessingHubThreadPool(ExecutorService threadPool) {
        if (threadPool != null) {
            processingHub.setThreadPool(threadPool);
        }
    }

    /**
     * 设置 BaseLinkManager 心率间隔，默认90
     *
     * @param interval 间隔，单位秒
     */
    public void setBaseLinkManagerHeartBeatInterval(int interval) {
        if (interval > 0) {
            baseLinkManager.setHeartBeatInterval(interval);
        }
    }
    /**
     * 设置 FileLinkManager 心率间隔，默认90
     *
     * @param interval 间隔，单位秒
     */
    public void setFileLinkManagerHeartBeatInterval(int interval) {
        if (interval > 0) {
            fileLinkManager.setHeartBeatInterval(interval);
        }
    }

    /**
     * 设置 BaseLinkManager 最大连接计数，默认10000
     *
     * @param maxLinkCount 最大连接数
     */
    public void setBaseLinkManagerMaxLinkCount(int maxLinkCount) {
        if (maxLinkCount > 0) {
            baseLinkManager.setMaxLinkCount(maxLinkCount);
        }
    }
    /**
     * 设置 FileLinkManager 最大连接计数，默认10000
     *
     * @param maxLinkCount 最大连接数
     */
    public void setFileLinkManagerMaxLinkCount(int maxLinkCount) {
        if (maxLinkCount > 0) {
            fileLinkManager.setMaxLinkCount(maxLinkCount);
        }
    }

    /**
     * 设置令牌桶，默认 capacity-5000 rate-1000
     *
     * @param capacity 令牌桶的总容量
     * @param rate 每秒产生的令牌数
     */
    public void setTokenBucket(long capacity, long rate) {
        listeners.setTokenBucket(new TokenBucket(capacity, rate));
    }
    /**
     * 设置网络过滤器，默认关闭，即不过滤任何连接
     *
     * @param netFilter 网络过滤器
     */
    public void setNetFilter(NetFilter netFilter) {
        listeners.setNetFilter(netFilter);
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
    public void submitSendTransferSchedule(String taskId, AbstractTransferSchedule abstractTransferSchedule) {
        processingHub.submitSendTransferSchedule(taskId, abstractTransferSchedule);
    }
    public void submitReceiveTransferSchedule(String taskId, AbstractTransferSchedule abstractTransferSchedule) {
        processingHub.submitReceiveTransferSchedule(taskId, abstractTransferSchedule);
    }
    public void addHandler(int way, ImtpsHandler handler) {
        processingHub.addHandler(way, Type.DEFAULT, Extra.DEFAULT, handler);
    }
    public void addHandler(int way, int type, ImtpsHandler handler) {
        processingHub.addHandler(way, type, Extra.DEFAULT, handler);
    }
    public void addHandler(int way, int type, int extra, ImtpsHandler handler) {
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

    /**
     * 注册selectionKey和UID绑定，在用户登录成功后调用此方法
     *
     * @param selectionKey 选择键
     * @param UID 待注册的UID
     * @param addition 生成Token的额外字符串，非必须
     * @return boolean 注册的UID已存在返回false，反之返回true
     */
    public boolean register(SelectionKey selectionKey, String UID, String... addition) {
        String token = Tool.produceToken(selectionKey, addition);
        if (linkTable.register(selectionKey, UID, token)) {
            putDataPacket(selectionKey, new DataPacket(Way.TOKEN_VERIFY, new TextDataBody(token)));
            return true;
        } else {
            return false;
        }
    }
    /**
     * 注销UID的登录状态，使其客户端退出登录
     *
     * @param UID uid
     * @param reason 原因
     */
    public void cancel(String UID, String... reason) {
        if (reason == null) {
            baseLinkManager.cancel(linkTable.getBaseSelectionKey(UID), "服务端主动关闭");
        } else {
            baseLinkManager.cancel(linkTable.getBaseSelectionKey(UID), reason[0]);
        }
    }
    public void cancel(SelectionKey selectionKey, String... reason) {
        if (reason == null) {
            baseLinkManager.cancel(selectionKey, "服务端主动关闭");
        } else {
            baseLinkManager.cancel(selectionKey, reason[0]);
        }
    }

    public void putDataPacket(String UID, DataPacket dataPacket) {
        if (UID == null || dataPacket == null) {
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "UID或dataPacket为null");
            return;
        }
        dataPacket.setUID(UID);
        if (dataPacket.baseLinkTransfer() && dataPacket.getDataBodySize() <= 1024*1024) {
            SelectionKey baseSelectionKey = linkTable.getBaseSelectionKey(UID);
            if (baseSelectionKey != null) {
                baseLinkManager.putDataPacket(baseSelectionKey, dataPacket);
            } else {
                imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "发送数据包时，目标UID [$] 不存在", UID);
            }
        } else {
            switch (linkTable.getFileLinkState(UID)) {
                case LinkTable.LINKSTATE_READY -> {
                    fileLinkManager.putDataPacket(linkTable.getFileSelectionKey(UID), dataPacket);
                }
                case LinkTable.LINKSTATE_LINKED, LinkTable.LINKSTATE_LINKING -> {
                    linkTable.getCacheDataPacketQueue(UID).add(dataPacket);
                }
                case LinkTable.LINKSTATE_CLOSED -> {
                    SelectionKey baseSelectionKey = linkTable.getBaseSelectionKey(UID);
                    if (baseSelectionKey != null) {
                        try {
                            SocketAddress address = ((SocketChannel) linkTable.getBaseSelectionKey(UID).channel()).getRemoteAddress();
                            baseLinkManager.putDataPacket(baseSelectionKey, new DataPacket(Way.BUILD_LINK, Type.FILE_LINK,
                                    new TextDataBody(addressManager.getFileLinkAddress(address.toString().split("[/:]")[1])))
                                    .setUID(UID).setSelectionKey(baseSelectionKey));
                            linkTable.setFileLinkState(UID, LinkTable.LINKSTATE_LINKING);
                            linkTable.getCacheDataPacketQueue(UID).add(dataPacket);
                        } catch (IOException e) {
                            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "发送文件连接地址出错", e);
                        }
                    } else {
                        imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "发送数据包时，目标UID [$] 不存在", UID);
                    }
                }
                default -> {
                    imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "发送数据包时，目标UID [$] 不存在", UID);
                }
            }
        }
    }
    public void putDataPacket(SelectionKey selectionKey, DataPacket dataPacket) {
        if (dataPacket.baseLinkTransfer() && dataPacket.getDataBodySize() <= 1024*1024) {
            baseLinkManager.putDataPacket(selectionKey, dataPacket);
        } else {
            fileLinkManager.putDataPacket(selectionKey, dataPacket);
        }
    }

    public ConcurrentHashMap<String, String> getTokenVerifyHashMap() {
        return linkTable.getTokenVerifyHashMap();
    }
    public void setTokenVerifyHashMap(ConcurrentHashMap<String, String> tokenVerifyHashMap) {
        if (tokenVerifyHashMap != null) {
            linkTable.setTokenVerifyHashMap(tokenVerifyHashMap);
        }
    }

    /**
     * 获取地址管理器
     *
     * @return {@link AddressManager }
     */
    public AddressManager getAddressManger() {
        return addressManager;
    }
}
