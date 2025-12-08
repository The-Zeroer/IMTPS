package com.thezeroer.imtps.server;

import com.thezeroer.imtps.server.address.AddressManager;
import com.thezeroer.imtps.server.datapacket.DataPacket;
import com.thezeroer.imtps.server.datapacket.PacketCodec;
import com.thezeroer.imtps.server.datapacket.databody.AbstractDataBody;
import com.thezeroer.imtps.server.datapacket.databody.FileDataBody;
import com.thezeroer.imtps.server.datapacket.databody.ObjectDataBody;
import com.thezeroer.imtps.server.datapacket.databody.TextDataBody;
import com.thezeroer.imtps.server.process.HandlerRegistry;
import com.thezeroer.imtps.server.process.TaskWrap;
import com.thezeroer.imtps.server.process.handler.ImtpsHandler;
import com.thezeroer.imtps.server.process.task.AbstractTask;
import com.thezeroer.imtps.server.process.DataPacketDispatcher;
import com.thezeroer.imtps.server.session.ImtpsSession;
import com.thezeroer.imtps.server.session.channel.ImtpsChannel;
import com.thezeroer.imtps.server.worker.Inspecter.Inspecter;
import com.thezeroer.imtps.server.worker.SessionAcceptor;
import com.thezeroer.imtps.server.worker.SessionFilter;
import com.thezeroer.imtps.server.worker.SessionHandshaker;
import com.thezeroer.imtps.server.worker.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.IOException;
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
    private static final Logger log = LoggerFactory.getLogger(IMTPS_Server.class);
    private final SessionAcceptor sessionAcceptor;
    private final SessionFilter sessionFilter;
    private final SessionHandshaker sessionHandshaker;
    private final SessionManager sessionManager;
    private final PacketCodec packetCodec;
    private final DataPacketDispatcher dataPacketDispatcher;
    private final AddressManager addressManager;

    public IMTPS_Server() throws IOException {
        addressManager = new AddressManager();
        packetCodec = new PacketCodec();
        dataPacketDispatcher = new DataPacketDispatcher(packetCodec);
        sessionManager = new SessionManager(packetCodec, dataPacketDispatcher, addressManager);
        sessionHandshaker = new SessionHandshaker(sessionManager);
        sessionFilter = new SessionFilter(sessionHandshaker, sessionManager);
        sessionAcceptor = new SessionAcceptor(sessionFilter);
        dataPacketDispatcher.transmitObject(sessionManager);

        packetCodec.registerDataBody(TextDataBody::new);
        packetCodec.registerDataBody(FileDataBody::new);
        packetCodec.registerDataBody(ObjectDataBody::new);
    }

    /**
     * 开始运行
     */
    public void startRunning() {
        log.info("Starting IMTPS-Server");
        sessionAcceptor.startRunning();
        sessionFilter.startRunning();
        sessionHandshaker.startRunning();
        sessionManager.startRunning();
        log.info("IMTPS-Server started");
    }
    /**
     * 停止运行
     */
    public void stopRunning() {
        log.info("Stopping IMTPS-Server");
        sessionAcceptor.stopRunning();
        sessionFilter.stopRunning();
        sessionHandshaker.stopRunning();
        sessionManager.stopRunning();
        log.info("IMTPS-Server stopped");
    }
    /**
     * 关闭
     */
    public void shutdown() {
        log.info("Shutting down IMTPS-Server");
        sessionAcceptor.shutdown();
        sessionFilter.shutdown();
        sessionHandshaker.shutdown();
        sessionManager.shutdown();
        log.info("IMTPS-Server shut down");
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
            log.info("[{}]端口[{}]绑定成功", channelType.name(), binPort);
        } catch (IOException e) {
            log.error("[{}]端口[{}]绑定失败", channelType.name(), port, e);
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
            log.info("[{}]端口[{}:{}]绑定成功", channelType.name(), hostName, binPort);
        } catch (IOException e) {
            log.error("[{}]端口[{}:{}]绑定失败", channelType.name(), hostName, port, e);
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
     * @param sessionName 会话名称
     */
    public boolean submitTask(AbstractTask<?> task, String sessionName) {
        if (task != null && sessionManager.getNameToSessionHashMap().get(sessionName) instanceof ImtpsSession imtpsSession) {
            dataPacketDispatcher.submitTask(new TaskWrap(task).setImtpsSession(imtpsSession));
            return true;
        }
        return false;
    }
    /**
     * 提交任务
     *
     * @param taskWrap 任务包装
     * @param sessionName 会话名称
     * @return boolean
     */
    public boolean submitTask(TaskWrap taskWrap, String sessionName) {
        if (taskWrap != null && sessionManager.getNameToSessionHashMap().get(sessionName) instanceof ImtpsSession imtpsSession) {
            dataPacketDispatcher.submitTask(taskWrap.setImtpsSession(imtpsSession));
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
        dataPacketDispatcher.removeTask(taskId);
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
     * 注册数据包处理程序
     *
     * @param matcher 匹配器
     * @param handler 处理程序
     * @return {@link IMTPS_Server }
     */
    public IMTPS_Server registerHandler(HandlerRegistry.PathsMatcher matcher, ImtpsHandler handler) {
        dataPacketDispatcher.registerHandler(matcher, handler);
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
            packetCodec.registerDataBody(constructor);
        }
        return this;
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
     * 设置数据包分发器线程池
     *
     * @param threadPool 线程池
     * @return {@link IMTPS_Server }
     */
    public IMTPS_Server setPacketDispatcherThreadPool(ExecutorService threadPool) {
        if (threadPool != null) {
            dataPacketDispatcher.setThreadPool(threadPool);
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
