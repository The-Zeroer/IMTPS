package imtps.client;

import imtps.client.datapacket.DataPacket;
import imtps.client.datapacket.PacketHandler;
import imtps.client.datapacket.databody.AbstractDataBody;
import imtps.client.datapacket.databody.FileDataBody;
import imtps.client.datapacket.databody.ObjectDataBody;
import imtps.client.datapacket.databody.TextDataBody;
import imtps.client.event.ImtpsEventCatch;
import imtps.client.log.ImtpsLogger;
import imtps.client.process.ImtpsHandler;
import imtps.client.process.ImtpsTask;
import imtps.client.process.ProcessingHub;
import imtps.client.worker.SessionHandshaker;
import imtps.client.worker.SessionManager;
import imtps.client.session.channel.AcceptChannel;
import imtps.client.session.channel.ImtpsChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
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

    public IMTPS_Client() throws IOException {
        imtpsLogger = new ImtpsLogger();
        packetHandler = new PacketHandler(imtpsLogger);
        processingHub = new ProcessingHub(packetHandler, imtpsLogger);
        sessionManager = new SessionManager(packetHandler, processingHub, imtpsLogger);
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
    public boolean LinkServer(String host, int port) throws Exception {
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "正在连接服务器[$:$]", host, port);
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(host, port));
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "已连接至服务器");
        return sessionHandshaker.transmit(new AcceptChannel(socketChannel, ImtpsChannel.TYPE.Control));
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
     * @param imtpsTask 任务
     */
    public void submitTask(ImtpsTask imtpsTask) {
        if (imtpsTask != null) {
            processingHub.submitTask(imtpsTask);
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
     * @param way 方法
     * @param handler 处理器
     */
    public IMTPS_Client registerHandler(int way, ImtpsHandler handler) {
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
    public IMTPS_Client registerHandler(int way, int type, ImtpsHandler handler) {
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
    public IMTPS_Client registerHandler(int way, int type, int extra, ImtpsHandler handler) {
        processingHub.registerHandler(way, type, extra, handler);
        return this;
    }
    /**
     * 移除处理程序
     *
     * @param way 方法
     */
    public IMTPS_Client removeHandler(int way) {
        processingHub.removeHandler(way, DataPacket.TYPE.DEFAULT, DataPacket.EXTRA.DEFAULT);
        return this;
    }
    /**
     * 移除处理程序
     *
     * @param way 方法
     * @param type 类型
     */
    public IMTPS_Client removeHandler(int way, int type) {
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
    public IMTPS_Client removeHandler(int way, int type, int extra) {
        processingHub.removeHandler(way, type, extra);
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
}
