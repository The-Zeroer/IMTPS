package imtp.client.process;

import imtp.client.IMTPS_Client;
import imtp.client.datapacket.DataPacket;
import imtp.client.log.ImtpLogger;

import java.util.concurrent.*;

/**
 * 处理中心
 *
 * @author NiZhanBo
 * @since 2025/01/25
 * @version 1.0.0
 */
public class ProcessingHub {
    private final IMTPS_Client imtpClient;
    private final ConcurrentHashMap<String, ImtpTask> taskHashMap;
    private final ConcurrentHashMap<String, ImtpHandler> handlerHashMap;
    private final ConcurrentHashMap<String, TransferSchedule> sendScheduleHashMap;
    private final ConcurrentHashMap<String, TransferSchedule> receiveScheduleHashMap;
    private ExecutorService threadPool;

    private final ImtpLogger imtpLogger;

    public ProcessingHub(IMTPS_Client imtpClient, ImtpLogger imtpLogger) {
        taskHashMap = new ConcurrentHashMap<>();
        handlerHashMap = new ConcurrentHashMap<>();
        sendScheduleHashMap = new ConcurrentHashMap<>();
        receiveScheduleHashMap = new ConcurrentHashMap<>();
        this.imtpClient = imtpClient;
        this.imtpLogger = imtpLogger;

        int poolSize = Runtime.getRuntime().availableProcessors();
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1024);
        RejectedExecutionHandler policy = new ThreadPoolExecutor.AbortPolicy();
        threadPool = new ThreadPoolExecutor(poolSize, poolSize * 2, 180, TimeUnit.SECONDS, queue, policy);
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public void submitTask(ImtpTask imtpTask) {
        taskHashMap.put(imtpTask.getTaskId(), imtpTask);
        imtpTask.setThreadPool(threadPool);
        imtpTask.setProcessingHub(this);
        imtpTask.request();
        imtpTask.startTime();
    }
    public void removeTask(String taskId) {
        taskHashMap.remove(taskId);
    }
    public void addHandler(int way, int type, int extra, ImtpHandler handler) {
        handlerHashMap.put(way + "-" + type + "-" + extra, handler);
    }
    public void removeHandler(int way, int type, int extra) {
        handlerHashMap.remove(way + "-" + type + "-" + extra);
    }
    public void submitSendTransferSchedule(String taskId, TransferSchedule transferSchedule) {
        sendScheduleHashMap.put(taskId, transferSchedule);
    }
    public void submitReceiveTransferSchedule(String taskId, TransferSchedule transferSchedule) {
        receiveScheduleHashMap.put(taskId, transferSchedule);
    }
    public TransferSchedule getSendTransferSchedule(String taskId) {
        return sendScheduleHashMap.remove(taskId);
    }
    public TransferSchedule getReceiveTransferSchedule(String taskId) {
        return receiveScheduleHashMap.remove(taskId);
    }

    public void work(DataPacket dataPacket) {
        if (taskHashMap.remove(dataPacket.getTaskId()) instanceof ImtpTask task) {
            task.execute(dataPacket);
        } else {
            ImtpHandler handler = handlerHashMap.get(dataPacket.getHeadCode());
            if (handler == null) {
                imtpLogger.log(ImtpLogger.LEVEL_WARN, "ImtpHandler [$] 缺失", dataPacket.getHeadCode());
            } else {
                threadPool.submit(() -> handler.handle(new ImtpExchange(imtpClient, dataPacket)));
            }
        }
    }
}