package imtps.server.process;

import imtps.server.IMTPS_Server;
import imtps.server.datapacket.DataPacket;
import imtps.server.datapacket.code.Way;
import imtps.server.log.ImtpsLogger;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.*;

/**
 * 处理中心
 *
 * @author NiZhanBo
 * @since 2025/01/25
 * @version 1.0.0
 */
public class ProcessingHub {
    private final IMTPS_Server imtpServer;
    private final ConcurrentHashMap<String, ImtpsTask> taskHashMap;
    private final ConcurrentHashMap<String, ImtpsHandler> handlerHashMap;
    private final ConcurrentHashMap<String, AbstractTransferSchedule> sendScheduleHashMap;
    private final ConcurrentHashMap<String, AbstractTransferSchedule> receiveScheduleHashMap;
    private ExecutorService threadPool;

    private final ImtpsLogger imtpsLogger;

    public ProcessingHub(IMTPS_Server imtpServer, ImtpsLogger imtpsLogger) {
        taskHashMap = new ConcurrentHashMap<>();
        handlerHashMap = new ConcurrentHashMap<>();
        sendScheduleHashMap = new ConcurrentHashMap<>();
        receiveScheduleHashMap = new ConcurrentHashMap<>();
        this.imtpServer = imtpServer;
        this.imtpsLogger = imtpsLogger;

        int poolSize = Runtime.getRuntime().availableProcessors();
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1024);
        RejectedExecutionHandler policy = new ThreadPoolExecutor.AbortPolicy();
        threadPool = new ThreadPoolExecutor(poolSize, poolSize * 2, 180, TimeUnit.SECONDS, queue, policy);
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public void submitTask(ImtpsTask imtpsTask) {
        threadPool.submit(() -> {
            if (imtpsTask.request()) {
                taskHashMap.put(imtpsTask.getTaskId(), imtpsTask);
                imtpsTask.setThreadPool(threadPool);
                imtpsTask.setProcessingHub(this);
                imtpsTask.startTime();
            }
        });
    }
    public void removeTask(String taskId) {
        taskHashMap.remove(taskId);
    }
    public void addHandler(int way, int type, int extra, ImtpsHandler handler) {
        handlerHashMap.put(way + "-" + type + "-" + extra, handler);
    }
    public void removeHandler(int way, int type, int extra) {
        handlerHashMap.remove(way + "-" + type + "-" + extra);
    }
    public void submitSendTransferSchedule(String taskId, AbstractTransferSchedule abstractTransferSchedule) {
        sendScheduleHashMap.put(taskId, abstractTransferSchedule);
    }
    public void submitReceiveTransferSchedule(String taskId, AbstractTransferSchedule abstractTransferSchedule) {
        receiveScheduleHashMap.put(taskId, abstractTransferSchedule);
    }
    public AbstractTransferSchedule getSendTransferSchedule(String taskId) {
        return sendScheduleHashMap.remove(taskId);
    }
    public AbstractTransferSchedule getReceiveTransferSchedule(String taskId) {
        return receiveScheduleHashMap.remove(taskId);
    }

    public void work(DataPacket dataPacket, boolean passVerify) {
        if (taskHashMap.remove(dataPacket.getTaskId()) instanceof ImtpsTask task) {
            task.execute(dataPacket);
        } else {
            ImtpsHandler handler = handlerHashMap.get(dataPacket.getHeadCode());
            if (handler == null) {
                imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "ImtpHandler [$] 缺失", dataPacket.getHeadCode());
            } else {
                if (passVerify || !handler.needVerify()) {
                    threadPool.submit(() -> {
                        handler.handle(new ImtpsExchange(imtpServer, dataPacket));
                    });
                } else {
                    String remoteAddress = "null";
                    try {
                        remoteAddress = ((SocketChannel)dataPacket.getSelectionKey().channel()).getRemoteAddress().toString();
                    } catch (IOException ignored) {}
                    imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "未验证的连接 [$] 访问ImtpHandler [$]", remoteAddress, dataPacket.getHeadCode());
                    imtpServer.putDataPacket(dataPacket.getSelectionKey(), new DataPacket(Way.ANSWER_NOT_VERIFY));
                }
            }
        }
    }
}