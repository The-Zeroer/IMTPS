package com.thezeroer.imtps.client;

import com.thezeroer.imtps.client.address.AddressManager;
import com.thezeroer.imtps.client.datapacket.DataPacket;
import com.thezeroer.imtps.client.datapacket.PacketHandler;
import com.thezeroer.imtps.client.datapacket.databody.AbstractDataBody;
import com.thezeroer.imtps.client.datapacket.databody.FileDataBody;
import com.thezeroer.imtps.client.datapacket.databody.ObjectDataBody;
import com.thezeroer.imtps.client.datapacket.databody.TextDataBody;
import com.thezeroer.imtps.client.event.ImtpsEventCatch;
import com.thezeroer.imtps.client.log.ImtpsLogger;
import com.thezeroer.imtps.client.process.handler.ImtpsHandler;
import com.thezeroer.imtps.client.process.task.AbstractTask;
import com.thezeroer.imtps.client.process.ProcessingHub;
import com.thezeroer.imtps.client.session.channel.AcceptChannel;
import com.thezeroer.imtps.client.session.channel.ImtpsChannel;
import com.thezeroer.imtps.client.worker.SessionHandshaker;
import com.thezeroer.imtps.client.worker.SessionManager;

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
    private final SessionHandshaker sessionHandshaker;
    private final SessionManager sessionManager;
    private final PacketHandler packetHandler;
    private final ProcessingHub processingHub;
    private final ImtpsLogger imtpsLogger;
    private final AddressManager addressManager;

    public IMTPS_Client() throws IOException {
        imtpsLogger = new ImtpsLogger();
        addressManager = new AddressManager();
        packetHandler = new PacketHandler(imtpsLogger);
        processingHub = new ProcessingHub(packetHandler, imtpsLogger);
        sessionManager = new SessionManager(packetHandler, processingHub, addressManager, imtpsLogger);
        sessionHandshaker = new SessionHandshaker(sessionManager, imtpsLogger);
        sessionManager.transmitObject(sessionHandshaker);
        processingHub.transmitObject(sessionManager);

        packetHandler.registerDataBody(TextDataBody::new);
        packetHandler.registerDataBody(FileDataBody::new);
        packetHandler.registerDataBody(ObjectDataBody::new);
    }

    /**
     * 连接服务器
     *
     * @param host 主机
     * @param port 端口
     */
    public boolean linkServer(String host, int port) throws Exception {
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "正在连接服务器[$:$]", host, port);
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(host, port));
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "已连接至服务器");
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
        sessionManager.startRunning();
    }
    /**
     * 停止运行
     *
     */
    public void stopRunning() {
        sessionManager.stopRunning();
    }
    /**
     * 关闭
     *
     */
    public void shutdown() {
        sessionManager.shutdown();
    }

    /**
     * 提交任务
     *
     * @param task 任务
     */
    public void submitTask(AbstractTask<?> task) {
        if (task != null) {
            processingHub.submitTask(task);
        }
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
     * @param handler 处理器
     */
    public IMTPS_Client registerHandler(ImtpsHandler handler) {
        processingHub.registerHandler(handler);
        return this;
    }
    /**
     * 处理中心冻结，完成注册后 freeze 提升查找性能
     *
     * @return {@link IMTPS_Client }
     */
    public IMTPS_Client processingHubFreeze() {
        processingHub.freezeHandlerTrieRouter();
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
            packetHandler.registerDataBody(constructor);
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
            processingHub.setThreadPool(threadPool);
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
