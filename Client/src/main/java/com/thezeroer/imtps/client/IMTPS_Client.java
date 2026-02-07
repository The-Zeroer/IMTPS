package com.thezeroer.imtps.client;

import com.thezeroer.imtps.client.address.AddressManager;
import com.thezeroer.imtps.client.datapacket.DataPacket;
import com.thezeroer.imtps.client.datapacket.PacketCodec;
import com.thezeroer.imtps.client.datapacket.databody.AbstractDataBody;
import com.thezeroer.imtps.client.datapacket.databody.FileDataBody;
import com.thezeroer.imtps.client.datapacket.databody.ObjectDataBody;
import com.thezeroer.imtps.client.datapacket.databody.TextDataBody;
import com.thezeroer.imtps.client.event.ImtpsEventCatch;
import com.thezeroer.imtps.client.process.HandlerRegistry;
import com.thezeroer.imtps.client.process.TaskWrap;
import com.thezeroer.imtps.client.process.handler.ImtpsHandler;
import com.thezeroer.imtps.client.process.task.AbstractTask;
import com.thezeroer.imtps.client.process.DataPacketDispatcher;
import com.thezeroer.imtps.client.session.channel.AcceptChannel;
import com.thezeroer.imtps.client.session.channel.ImtpsChannel;
import com.thezeroer.imtps.client.worker.SessionHandshaker;
import com.thezeroer.imtps.client.worker.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * 即时传输协议 客户端
 *
 * @author NiZhanBo
 * @since 2025/06/29
 * @version 1.0.0
 */
public class IMTPS_Client {
    private static final Logger log = LoggerFactory.getLogger(IMTPS_Client.class);
    private final SessionHandshaker sessionHandshaker;
    private final SessionManager sessionManager;
    private final PacketCodec packetCodec;
    private final DataPacketDispatcher dataPacketDispatcher;
    private final AddressManager addressManager;

    public IMTPS_Client() throws IOException {
        addressManager = new AddressManager();
        packetCodec = new PacketCodec();
        dataPacketDispatcher = new DataPacketDispatcher(packetCodec);
        sessionManager = new SessionManager(packetCodec, dataPacketDispatcher, addressManager);
        sessionHandshaker = new SessionHandshaker(sessionManager);
        sessionManager.transmitObject(sessionHandshaker);
        dataPacketDispatcher.transmitObject(sessionManager);

        packetCodec.registerDataBody(TextDataBody::new);
        packetCodec.registerDataBody(FileDataBody::new);
        packetCodec.registerDataBody(ObjectDataBody::new);
    }

    /**
     * 连接服务器
     *
     * @param host 主机
     * @param port 端口
     */
    public boolean linkServer(String host, int port) throws Exception {
        log.info("正在连接服务端[{}:{}]", host, port);
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(host, port));
        log.info("已连接至服务端[{}:{}]", host, port);
        addressManager.setServerHostName(host);
        return sessionHandshaker.transmit(new AcceptChannel(socketChannel, ImtpsChannel.TYPE.Control));
    }
    /**
     * 判断是否连接至服务器
     *
     * @return boolean
     */
    public boolean isConnected() {
        return sessionManager.getImtpsSession() != null;
    }

    /**
     * 开始运行
     *
     */
    public void startRunning() {
        log.info("Starting IMTPS-Client");
        sessionManager.startRunning();
        log.info("IMTPS-Client started");
    }
    /**
     * 停止运行
     *
     */
    public void stopRunning() {
        log.info("Stopping IMTPS-Client");
        sessionManager.stopRunning();
        log.info("IMTPS-Client stopped");
    }
    /**
     * 关闭
     *
     */
    public void shutdown() {
        log.info("Shutting down IMTPS-Client");
        sessionManager.shutdown();
        log.info("IMTPS-Client shut down");
    }

    /**
     * 提交任务
     *
     * @param task 任务
     */
    public void submitTask(AbstractTask<?> task) {
        if (task != null) {
            dataPacketDispatcher.submitTask(new TaskWrap(task));
        }
    }
    /**
     * 提交任务
     *
     * @param taskWrap 任务包装
     */
    public void submitTask(TaskWrap taskWrap) {
        if (taskWrap != null) {
            dataPacketDispatcher.submitTask(taskWrap);
        }
    }
    /**
     * 删除任务
     *
     * @param taskId 任务 ID
     */
    public void removeTask(String taskId) {
        dataPacketDispatcher.removeTask(taskId);
    }

    /**
     * 注册数据包处理程序
     *
     * @param matcher 匹配器
     * @param handler 处理程序
     * @return {@link IMTPS_Client }
     */
    public IMTPS_Client registerHandler(HandlerRegistry.PathMatcher matcher, ImtpsHandler handler) {
        dataPacketDispatcher.registerHandler(matcher, handler);
        return this;
    }

    /**
     * 发送数据包
     *
     * @param dataPacket 数据包
     */
    public IMTPS_Client sendDataPacket(DataPacket dataPacket) {
        sessionManager.putDataPacket(dataPacket);
        return this;
    }

    /**
     * 添加自定义数据体
     *
     * @param constructor 构造函数
     */
    public IMTPS_Client registerDataBody(Supplier<? extends AbstractDataBody<?>> constructor) {
        if (constructor != null) {
            packetCodec.registerDataBody(constructor);
        }
        return this;
    }

    /**
     * 设置会话心跳间隔，单位秒
     *
     * @param interval 间隔
     */
    public IMTPS_Client setSessionHeartBeatInterval(int interval) {
        if (interval > 0) {
            sessionManager.setHeartBeatInterval(interval);
        }
        return this;
    }
    /**
     * 设置会话管理器线程池
     *
     * @param threadPool 线程池
     */
    public IMTPS_Client setSessionManagerThreadPool(ExecutorService threadPool) {
        if (threadPool != null) {
            sessionManager.setThreadPool(threadPool);
        }
        return this;
    }
    /**
     * 设置处理中心线程池
     *
     * @param threadPool 线程池
     * @return {@link IMTPS_Client }
     */
    public IMTPS_Client setProcessingHubThreadPool(ExecutorService threadPool) {
        if (threadPool != null) {
            dataPacketDispatcher.setThreadPool(threadPool);
        }
        return this;
    }
    /**
     * 设置 IMTPS 事件捕获
     *
     * @param imtpsEventCatch IMTPS 事件捕获
     */
    public IMTPS_Client setImtpsEventCatch(ImtpsEventCatch imtpsEventCatch) {
        if (imtpsEventCatch != null) {
            sessionManager.setImtpsEventCatch(imtpsEventCatch);
        }
        return this;
    }

    /**
     * 设置文件缓存路径，默认路径为".\ImtpFileCache\"，创建失败时路径为".\"
     *
     * @param fileCachePath 文件缓存路径
     */
    public IMTPS_Client setFileCachePath(Path fileCachePath) throws IOException {
        if (fileCachePath != null) {
            FileDataBody.setFileCachePath(fileCachePath);
        }
        return this;
    }
}
