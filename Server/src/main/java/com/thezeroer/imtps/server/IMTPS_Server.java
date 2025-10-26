package com.thezeroer.imtps.server;

import com.thezeroer.imtps.server.address.AddressManager;
import com.thezeroer.imtps.server.datapacket.DataPacket;
import com.thezeroer.imtps.server.datapacket.PacketHandler;
import com.thezeroer.imtps.server.datapacket.databody.AbstractDataBody;
import com.thezeroer.imtps.server.datapacket.databody.FileDataBody;
import com.thezeroer.imtps.server.datapacket.databody.ObjectDataBody;
import com.thezeroer.imtps.server.datapacket.databody.TextDataBody;
import com.thezeroer.imtps.server.log.ImtpsLogger;
import com.thezeroer.imtps.server.log.LogHandler;
import com.thezeroer.imtps.server.process.handler.ImtpsHandler;
import com.thezeroer.imtps.server.process.task.AbstractTask;
import com.thezeroer.imtps.server.process.task.ImtpsTask;
import com.thezeroer.imtps.server.process.ProcessingHub;
import com.thezeroer.imtps.server.session.ImtpsSession;
import com.thezeroer.imtps.server.session.channel.ImtpsChannel;
import com.thezeroer.imtps.server.worker.Inspecter.Inspecter;
import com.thezeroer.imtps.server.worker.SessionAcceptor;
import com.thezeroer.imtps.server.worker.SessionFilter;
import com.thezeroer.imtps.server.worker.SessionHandshaker;
import com.thezeroer.imtps.server.worker.SessionManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * 即时传输协议 服务端
 *
 * @author NiZhanBo
 * @since 2025/06/29
 * @version 1.0.0
 */
public class IMTPS_Server {
    private final SessionAcceptor sessionAcceptor;
    private final SessionFilter sessionFilter;
    private final SessionHandshaker sessionHandshaker;
    private final SessionManager sessionManager;
    private final PacketHandler packetHandler;
    private final ProcessingHub processingHub;
    private final AddressManager addressManager;
    private final ImtpsLogger imtpsLogger;

    public IMTPS_Server() throws IOException {
        imtpsLogger = new ImtpsLogger();
        addressManager = new AddressManager();
        packetHandler = new PacketHandler(imtpsLogger);
        processingHub = new ProcessingHub(packetHandler, imtpsLogger);
        sessionManager = new SessionManager(packetHandler, processingHub, addressManager, imtpsLogger);
        sessionHandshaker = new SessionHandshaker(sessionManager, imtpsLogger);
        sessionFilter = new SessionFilter(sessionHandshaker, sessionManager, imtpsLogger);
        sessionAcceptor = new SessionAcceptor(sessionFilter, imtpsLogger);
        processingHub.transmitObject(sessionManager);

        packetHandler.registerDataBody(TextDataBody::new);
        packetHandler.registerDataBody(FileDataBody::new);
        packetHandler.registerDataBody(ObjectDataBody::new);
    }

    /**
     * 开始运行
     */
    public void startRunning() {
        sessionAcceptor.startRunning();
        sessionFilter.startRunning();
        sessionHandshaker.startRunning();
        sessionManager.startRunning();
    }
    /**
     * 停止运行
     */
    public void stopRunning() {
        sessionAcceptor.stopRunning();
        sessionFilter.stopRunning();
        sessionHandshaker.stopRunning();
        sessionManager.stopRunning();
    }
    /**
     * 关闭
     */
    public void shutdown() {
        sessionAcceptor.shutdown();
        sessionFilter.shutdown();
        sessionHandshaker.shutdown();
        sessionManager.shutdown();
    }

    /**
     * 绑定端口
     *
     * @param port        端口
     * @param channelType 通道类型
     */
    public IMTPS_Server bindPort(int port, ImtpsChannel.TYPE channelType) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        try {
            serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            serverSocketChannel.bind(new InetSocketAddress(port)).configureBlocking(false);
            sessionAcceptor.registerServerChannel(serverSocketChannel, channelType);
            int binPort = serverSocketChannel.socket().getLocalPort();
            addressManager.setLocalPort(channelType, binPort);
            imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "[$]端口[$]绑定成功", channelType.name(), binPort);
        } catch (IOException e) {
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "[$]端口[$]绑定失败", channelType.name(), port, e);
            serverSocketChannel.close();
            throw e;
        }
        return this;
    }
    /**
     * 绑定端口
     *
     * @param hostName    主机名
     * @param port        端口
     * @param channelType 通道类型
     */
    public IMTPS_Server bindPort(String hostName, int port, ImtpsChannel.TYPE channelType) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        try {
            serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            serverSocketChannel.bind(new InetSocketAddress(hostName, port)).configureBlocking(false);
            sessionAcceptor.registerServerChannel(serverSocketChannel, channelType);
            int binPort = serverSocketChannel.socket().getLocalPort();
            addressManager.setLocalPort(channelType, binPort);
            imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "[$]端口[$:$]绑定成功", channelType.name(), hostName, binPort);
        } catch (IOException e) {
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "[$]端口[$:$]绑定失败", channelType.name(), hostName, port, e);
            serverSocketChannel.close();
            throw e;
        }
        return this;
    }

    /**
     * 绑定所有端口
     *
     * @param controlPort 控制端口
     * @param dataBasicPort 数据基本端口
     * @param dataFilePort 数据文件端口
     * @return {@link IMTPS_Server }
     */
    public IMTPS_Server bindAllPort(int controlPort, int dataBasicPort, int dataFilePort) throws IOException {
        bindPort(controlPort, ImtpsChannel.TYPE.Control).bindPort(dataBasicPort, ImtpsChannel.TYPE.DataBasic)
                .bindPort(dataFilePort, ImtpsChannel.TYPE.DataFile);
        return this;
    }
    /**
     * 绑定所有端口
     *
     * @param controlPort 控制端口
     * @param dataBasicPort 数据基本端口
     * @param dataFilePort 数据文件端口
     * @return {@link IMTPS_Server }
     */
    public IMTPS_Server bindAllPort(String hostName, int controlPort, int dataBasicPort, int dataFilePort) throws IOException {
        bindPort(hostName, controlPort, ImtpsChannel.TYPE.Control).bindPort(hostName, dataBasicPort, ImtpsChannel.TYPE.DataBasic)
                .bindPort(hostName, dataFilePort, ImtpsChannel.TYPE.DataFile);
        return this;
    }

    /**
     * 提交任务
     *
     * @param task 任务
     */
    public boolean submitTask(AbstractTask<?> task, String sessionName) {
        if (task != null && sessionManager.getNameToSessionHashMap().get(sessionName) instanceof ImtpsSession imtpsSession) {
            processingHub.submitTask(task.setImtpsSession(imtpsSession));
            return true;
        }
        return false;
    }
    /**
     * 删除任务
     *
     * @param taskId 任务 ID
     */
    public IMTPS_Server removeTask(String taskId) {
        processingHub.removeTask(taskId);
        return this;
    }

    /**
     * 发送数据包
     *
     * @param dataPacket 数据包
     * @param sessionName 会话名称
     * @return boolean
     */
    public boolean sendDataPacket(DataPacket dataPacket, String sessionName) {
        if (sessionManager.getNameToSessionHashMap().get(sessionName) instanceof ImtpsSession imtpsSession) {
            sessionManager.putDataPacket(imtpsSession, dataPacket);
            return true;
        } else {
            return false;
        }
    }

    /**
     * 添加处理程序
     *
     * @param handler 处理器
     */
    public IMTPS_Server registerHandler(ImtpsHandler handler) {
        processingHub.registerHandler(handler);
        return this;
    }
    /**
     * 处理中心冻结，完成注册后 freeze 提升查找性能
     *
     * @return {@link IMTPS_Server }
     */
    public IMTPS_Server processingHubFreeze() {
        processingHub.freezeHandlerTrieRouter();
        return this;
    }

    /**
     * 添加检查器，默认名称为全类名
     *
     * @param inspecter 检查员
     * @return {@link IMTPS_Server }
     */
    public IMTPS_Server registerInspecter(Inspecter inspecter) {
        sessionFilter.registerInspecter(inspecter.getClass().getName(), inspecter);
        return this;
    }
    /**
     * 添加检查器
     *
     * @param name 名字
     * @param inspecter 检查员
     * @return {@link IMTPS_Server }
     */
    public IMTPS_Server registerInspecter(String name, Inspecter inspecter) {
        sessionFilter.registerInspecter(name, inspecter);
        return this;
    }
    /**
     * 移除检查器
     *
     * @param name 名字
     * @return {@link IMTPS_Server }
     */
    public IMTPS_Server removeInspecter(String name) {
        sessionFilter.removeInspecter(name);
        return this;
    }
    /**
     * 获取检查器
     *
     * @param name 名字
     * @return {@link Inspecter }
     */
    public Inspecter getInspecter(String name) {
        return sessionFilter.getInspecter(name);
    }

    /**
     * 添加自定义数据体
     *
     * @param constructor 构造函数
     */
    public IMTPS_Server registerDataBody(Supplier<? extends AbstractDataBody<?>> constructor) {
        if (constructor != null) {
            packetHandler.registerDataBody(constructor);
        }
        return this;
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
     * 设置文件缓存路径，默认路径为".\ImtpFileCache\"，创建失败时路径为".\"
     *
     * @param fileCachePath 文件缓存路径
     */
    public IMTPS_Server setFileCachePath(Path fileCachePath) throws IOException {
        if (fileCachePath != null) {
            FileDataBody.setFileCachePath(fileCachePath);
        }
        return this;
    }

    /**
     * 设置会话过滤器线程池
     *
     * @param threadPool 线程池
     */
    public IMTPS_Server setSessionFilterThreadPool(ExecutorService threadPool) {
        if (threadPool != null) {
            sessionFilter.setThreadPool(threadPool);
        }
        return this;
    }
    /**
     * 设置会话握手器线程池
     *
     * @param threadPool 线程池
     */
    public IMTPS_Server setSessionHandshakerThreadPool(ExecutorService threadPool) {
        if (threadPool != null) {
            sessionHandshaker.setThreadPool(threadPool);
        }
        return this;
    }
    /**
     * 设置会话管理器线程池
     *
     * @param threadPool 线程池
     */
    public IMTPS_Server setSessionManagerThreadPool(ImtpsChannel.TYPE type, ExecutorService threadPool) {
        if (threadPool != null) {
            sessionManager.setThreadPool(type, threadPool);
        }
        return this;
    }
    /**
     * 设置处理中心线程池
     *
     * @param threadPool 线程池
     * @return {@link IMTPS_Server }
     */
    public IMTPS_Server setProcessingHubThreadPool(ExecutorService threadPool) {
        if (threadPool != null) {
            processingHub.setThreadPool(threadPool);
        }
        return this;
    }
    /**
     * 设置会话心跳间隔，单位秒
     *
     * @param interval 间隔
     */
    public IMTPS_Server setSessionHeartBeatInterval(ImtpsChannel.TYPE type, int interval) {
        if (interval > 0) {
            sessionManager.setHeartBeatInterval(type, interval);
        }
        return this;
    }

    /**
     * 获取所有会话名称
     *
     * @return {@link Set }<{@link String }>
     */
    public Set<String> getAllSessionName() {
        return sessionManager.getNameToSessionHashMap().keySet();
    }

    public AddressManager getAddressManager() {
        return addressManager;
    }
}
