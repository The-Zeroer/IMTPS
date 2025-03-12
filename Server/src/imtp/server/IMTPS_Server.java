package imtp.server;

import imtp.server.datapacket.DataBodyManager;
import imtp.server.datapacket.DataPacket;
import imtp.server.datapacket.code.Extra;
import imtp.server.datapacket.code.Type;
import imtp.server.datapacket.code.Way;
import imtp.server.datapacket.databody.AbstractDataBody;
import imtp.server.datapacket.databody.ByteDataBody;
import imtp.server.datapacket.databody.FileDataBody;
import imtp.server.datapacket.databody.TextDataBody;
import imtp.server.link.BaseLinkManager;
import imtp.server.link.FileLinkManager;
import imtp.server.link.LinkTable;
import imtp.server.listeners.Listeners;
import imtp.server.log.ImtpLogger;
import imtp.server.log.LogHandler;
import imtp.server.process.ImtpHandler;
import imtp.server.process.ImtpTask;
import imtp.server.process.ProcessingHub;
import imtp.server.security.Secure;
import imtp.server.util.AddressManager;
import imtp.server.util.NetFilter;
import imtp.server.util.TokenBucket;
import imtp.server.util.Tool;

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
    private final LinkTable linkTable;
    private final BaseLinkManager baseLinkManager;
    private final FileLinkManager fileLinkManager;
    private final ProcessingHub processingHub;
    private final AddressManager addressManager;
    private final ImtpLogger imtpLogger;
    private final Secure secure;

    public IMTPS_Server() throws IOException, NoSuchAlgorithmException {
        secure = new Secure();
        imtpLogger = new ImtpLogger();
        linkTable = new LinkTable(imtpLogger);
        addressManager = new AddressManager();
        processingHub = new ProcessingHub(this, imtpLogger);
        fileLinkManager = new FileLinkManager(secure, linkTable, processingHub, imtpLogger);
        baseLinkManager = new BaseLinkManager(secure, linkTable, processingHub, imtpLogger, fileLinkManager, addressManager);
        listeners = new Listeners(baseLinkManager, fileLinkManager, imtpLogger);
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
            imtpLogger.log(ImtpLogger.LEVEL_ERROR, "基本端口冲突 [$] ", port);
            serverSocketChannel.close();
            throw e;
        }
        listeners.setBaseServerSocketChannel(serverSocketChannel);
        int binPort = serverSocketChannel.socket().getLocalPort();
        imtpLogger.log(ImtpLogger.LEVEL_INFO, "基本端口已绑定 [$] ", binPort);
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
            imtpLogger.log(ImtpLogger.LEVEL_ERROR, "文件端口冲突 [$] ", port);
            serverSocketChannel.close();
            throw e;
        }
        listeners.setFileServerSocketChannel(serverSocketChannel);
        int binPort = serverSocketChannel.socket().getLocalPort();
        imtpLogger.log(ImtpLogger.LEVEL_INFO, "文件端口已绑定 [$] ", binPort);
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
        listeners.startRunning();
        baseLinkManager.startRunning();
        fileLinkManager.startRunning();
        imtpLogger.log(ImtpLogger.LEVEL_INFO, "IMTP_Server开始运行，本机IP [$]", InetAddress.getLocalHost().getHostAddress());
    }
    /**
     * 停止监听端口，不接受新的连接，已建立的连接暂停服务
     */
    public void stopRunning() {
        listeners.stopRunning();
        baseLinkManager.stopRunning();
        fileLinkManager.stopRunning();
        imtpLogger.log(ImtpLogger.LEVEL_INFO, "IMTP_Server暂停运行");
    }
    /**
     * 停止监听端口，并关闭已建立的连接
     */
    public void shutdown() {
        listeners.shutdown();
        baseLinkManager.shutdown();
        fileLinkManager.shutdown();
        imtpLogger.log(ImtpLogger.LEVEL_INFO, "IMTP_Server停止运行");
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

    public boolean register(SelectionKey selectionKey, String UID, String... addition) {
        String token = Tool.produceToken(selectionKey, addition);
        if (linkTable.register(selectionKey, UID, token)) {
            putDataPacket(selectionKey, new DataPacket(Way.TOKEN_VERIFY, new TextDataBody(token)));
            return true;
        } else {
            return false;
        }
    }
    public void cancel(String UID) {
        linkTable.cancel(UID);
    }

    public void putDataPacket(String UID, DataPacket dataPacket) {
        if (UID == null || dataPacket == null) {
            imtpLogger.log(ImtpLogger.LEVEL_ERROR, "UID或dataPacket为null");
            return;
        }
        dataPacket.setUID(UID);
        if (dataPacket.baseLinkTransfer() && dataPacket.getDataBodySize() <= 1024*1024) {
            SelectionKey baseSelectionKey = linkTable.getBaseSelectionKey(UID);
            if (baseSelectionKey != null) {
                baseLinkManager.putDataPacket(baseSelectionKey, dataPacket);
            } else {
                imtpLogger.log(ImtpLogger.LEVEL_WARN, "发送数据包时，目标UID [$] 不存在", UID);
            }
        } else {
            switch (linkTable.getFileLinkState(UID)) {
                case LinkTable.LINKSTATE_READY -> {
                    fileLinkManager.putDataPacket(linkTable.getFileSelectionKey(UID), dataPacket);
                }
                case LinkTable.LINKSTATE_Linked, LinkTable.LINKSTATE_Linking -> {
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
                            linkTable.setFileLinkState(UID, LinkTable.LINKSTATE_Linking);
                            linkTable.getCacheDataPacketQueue(UID).add(dataPacket);
                        } catch (IOException e) {
                            imtpLogger.log(ImtpLogger.LEVEL_ERROR, "发送文件连接地址出错", e);
                        }
                    } else {
                        imtpLogger.log(ImtpLogger.LEVEL_WARN, "发送数据包时，目标UID [$] 不存在", UID);
                    }
                }
                default -> {
                    imtpLogger.log(ImtpLogger.LEVEL_WARN, "发送数据包时，目标UID [$] 不存在", UID);
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