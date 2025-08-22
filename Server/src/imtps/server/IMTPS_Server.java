package imtps.server;

import imtps.server.buffer.BufferManager;
import imtps.server.datapacket.DataPacket;
import imtps.server.datapacket.PacketHandler;
import imtps.server.datapacket.databody.AbstractDataBody;
import imtps.server.datapacket.databody.FileDataBody;
import imtps.server.datapacket.databody.ObjectDataBody;
import imtps.server.datapacket.databody.TextDataBody;
import imtps.server.log.ImtpsLogger;
import imtps.server.process.ImtpsHandler;
import imtps.server.process.ImtpsTask;
import imtps.server.process.ProcessingHub;
import imtps.server.session.*;
import imtps.server.address.AddressManager;
import imtps.server.worker.Inspecter.Inspecter;
import imtps.server.session.channel.ImtpsChannel;
import imtps.server.worker.SessionAcceptor;
import imtps.server.worker.SessionFilter;
import imtps.server.worker.SessionHandshaker;
import imtps.server.worker.SessionManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
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
            serverSocketChannel.bind(new InetSocketAddress(port)).configureBlocking(false);
            sessionAcceptor.registerServerChannel(serverSocketChannel, channelType);
            int binPort = serverSocketChannel.socket().getLocalPort();
            addressManager.setHostPort(channelType, binPort);
            imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "[$]端口[$]绑定成功", channelType.name(), binPort);
        } catch (IOException e) {
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "[$]端口[$]绑定失败", channelType.name(), port, e);
            serverSocketChannel.close();
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
            serverSocketChannel.bind(new InetSocketAddress(hostName, port)).configureBlocking(false);
            sessionAcceptor.registerServerChannel(serverSocketChannel, channelType);
            int binPort = serverSocketChannel.socket().getLocalPort();
            addressManager.setHostPort(channelType, binPort);
            imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "[$]端口[$:$]绑定成功", channelType.name(), hostName, binPort);
        } catch (IOException e) {
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "[$]端口[$:$]绑定失败", channelType.name(), hostName, port, e);
            serverSocketChannel.close();
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
     * 提交任务
     *
     * @param imtpsTask 任务
     */
    public boolean submitTask(ImtpsTask imtpsTask, String sessionName) {
        if (imtpsTask != null && sessionManager.getNameToSessionHashMap().get(sessionName) instanceof ImtpsSession imtpsSession) {
            processingHub.submitTask(imtpsTask.setImtpsSession(imtpsSession));
            return true;
        }
        return false;
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
     * 添加处理程序
     *
     * @param way 方法
     * @param handler 处理器
     */
    public IMTPS_Server registerHandler(int way, ImtpsHandler handler) {
        processingHub.registerHandler(way, DataPacket.TYPE.DEFAULT, DataPacket.EXTRA.DEFAULT, handler);
        return this;
    }
    /**
     * 添加处理程序
     *
     * @param way 方法
     * @param type 类型
     * @param handler 处理器
     */
    public IMTPS_Server registerHandler(int way, int type, ImtpsHandler handler) {
        processingHub.registerHandler(way, type, DataPacket.EXTRA.DEFAULT, handler);
        return this;
    }
    /**
     * 添加处理程序
     *
     * @param way 方法
     * @param type 类型
     * @param extra 额外
     * @param handler 处理器
     */
    public IMTPS_Server registerHandler(int way, int type, int extra, ImtpsHandler handler) {
        processingHub.registerHandler(way, type, extra, handler);
        return this;
    }
    /**
     * 移除处理程序
     *
     * @param way 方法
     */
    public IMTPS_Server removeHandler(int way) {
        processingHub.removeHandler(way, DataPacket.TYPE.DEFAULT, DataPacket.EXTRA.DEFAULT);
        return this;
    }
    /**
     * 移除处理程序
     *
     * @param way 方法
     * @param type 类型
     */
    public IMTPS_Server removeHandler(int way, int type) {
        processingHub.removeHandler(way, type, DataPacket.EXTRA.DEFAULT);
        return this;
    }
    /**
     * 移除处理程序
     *
     * @param way 方法
     * @param type 类型
     * @param extra 额外
     */
    public IMTPS_Server removeHandler(int way, int type, int extra) {
        processingHub.removeHandler(way, type, extra);
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
     * 设置文件缓存路径，默认路径为".\ImtpFileCache\"，创建失败时路径为".\"
     *
     * @param fileCachePath 文件缓存路径
     */
    public IMTPS_Server setFileCachePath(String fileCachePath) {
        if (fileCachePath != null) {
            FileDataBody.setFileCachePath(fileCachePath);
        }
        return this;
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
}
