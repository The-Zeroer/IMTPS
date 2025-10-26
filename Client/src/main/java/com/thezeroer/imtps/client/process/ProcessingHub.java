package com.thezeroer.imtps.client.process;

import com.thezeroer.imtps.client.datapacket.DataPacket;
import com.thezeroer.imtps.client.datapacket.PacketHandler;
import com.thezeroer.imtps.client.log.ImtpsLogger;
import com.thezeroer.imtps.client.process.handler.ImtpsContext;
import com.thezeroer.imtps.client.process.handler.ImtpsHandler;
import com.thezeroer.imtps.client.process.task.AbstractTask;
import com.thezeroer.imtps.client.process.task.ImtpsTask;
import com.thezeroer.imtps.client.process.task.ImtpsTaskSet;
import com.thezeroer.imtps.client.view.AbstractTransmitView;
import com.thezeroer.imtps.client.worker.SessionManager;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.*;

/**
 * 加工中心
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/31
 */
public class ProcessingHub {
    private final TrieRouter<ImtpsHandler> handlerTrieRouter;
    private final ConcurrentLinkedQueue<AbstractTask<?>> taskQueue;
    private final ConcurrentHashMap<String, AbstractTask<?>> taskHashMap;
    private final TaskTimer taskTimer;
    private ExecutorService threadPool;

    private SessionManager sessionManager;
    private final PacketHandler packetHandler;
    private final ImtpsLogger imtpsLogger;

    public ProcessingHub(PacketHandler packetHandler, ImtpsLogger imtpsLogger) {
        handlerTrieRouter = new TrieRouter<>();
        taskQueue = new ConcurrentLinkedQueue<>();
        taskHashMap = new ConcurrentHashMap<>();
        taskTimer = new TaskTimer();

        int poolSize = Runtime.getRuntime().availableProcessors();
        threadPool = new ThreadPoolExecutor(poolSize, poolSize * 2, 180
                , TimeUnit.SECONDS, new ArrayBlockingQueue<>(1024), new ThreadPoolExecutor.CallerRunsPolicy());

        this.packetHandler = packetHandler;
        this.imtpsLogger = imtpsLogger;
        packetHandler.setThreadPool(threadPool);
    }

    public void registerHandler(ImtpsHandler handler) {
        handlerTrieRouter.register(TrieRouter.buildMatcher(handler.getWayMatch())
                , TrieRouter.buildMatcher(handler.getTypeMach())
                , TrieRouter.buildMatcher(handler.getExtraMatch()), handler);
    }
    public void freezeHandlerTrieRouter() {
        handlerTrieRouter.freeze();
    }

    public void handleDataPacket(DataPacket dataPacket) {
        AbstractTask<?> mapTask = taskHashMap.get(dataPacket.getTaskId());
        if (mapTask != null) {
            threadPool.submit(() -> executeTaskResponse(mapTask, dataPacket));
            if (taskQueue.poll() instanceof AbstractTask<?> queuedTask) {
                threadPool.submit(() -> executeTaskRequest(queuedTask));
            }
            return;
        }
        ImtpsHandler handler = handlerTrieRouter.find(dataPacket.getWay(), dataPacket.getType(), dataPacket.getExtra());
        if (handler == null) {
            imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "ImtpHandler[$]缺失", dataPacket.getHeadCode());
            return;
        }
        threadPool.submit(() -> {
            try {
                handler.execute(new ImtpsContext(sessionManager, dataPacket));
            }catch (Exception e) {
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "ImtpHandler[$]出现未捕获的异常", dataPacket.getHeadCode(), e);
            }
        });
    }
    public void submitTask(AbstractTask<?> task) {
        switch (task.getPattern()) {
            case AbstractTask.PATTERN_DEFAULT -> threadPool.submit(() -> executeTaskRequest(task));
            case AbstractTask.PATTERN_QUEUE -> {
                if (taskHashMap.isEmpty()) {
                    threadPool.submit(() -> executeTaskRequest(task));
                } else {
                    taskQueue.add(task);
                }
            }
            case AbstractTask.PATTERN_WAIT -> {
                executeTaskRequest(task);
                synchronized (task) {
                    try {
                        task.wait(task.getWaitingTime());
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }
    public void removeTask(String taskId) {
        if (taskHashMap.remove(taskId) instanceof AbstractTask<?> task && task.getPattern() == AbstractTask.PATTERN_QUEUE) {
            taskQueue.remove(task);
        }
    }
    public void submitTaskToTimer(AbstractTask<?> task) {
        if (task != null) {
            taskTimer.submit(task);
        }
    }

    private void executeTaskRequest(AbstractTask<?> task) {
        try {
            if (task instanceof ImtpsTask) {
                if (task.request() instanceof DataPacket dataPacket) {
                    taskHashMap.put(task.getTaskId(), task);
                    if (task.getSendView() instanceof AbstractTransmitView sendView) {
                        packetHandler.addSendView(task.getTaskId(), sendView);
                    }
                    if (task.getReceiveView() instanceof AbstractTransmitView receiveView) {
                        packetHandler.addReceiveView(task.getTaskId(), receiveView);
                    }
                    sessionManager.putDataPacket(dataPacket.setTask(task));
                } else {
                    task.finish(false);
                }
            } else if (task instanceof ImtpsTaskSet imtpsTaskSet) {
                if (task.request() instanceof List<?> dataPacketList && !dataPacketList.isEmpty()) {
                    imtpsTaskSet.setDataPacketCount(dataPacketList.size());
                    taskHashMap.put(task.getTaskId(), task);
                    if (task.getSendView() instanceof AbstractTransmitView sendView) {
                        packetHandler.addSendView(task.getTaskId(), sendView);
                    }
                    if (task.getReceiveView() instanceof AbstractTransmitView receiveView) {
                        packetHandler.addReceiveView(task.getTaskId(), receiveView);
                    }
                    for (Object object : dataPacketList) {
                        if (object instanceof DataPacket dataPacket) {
                            sessionManager.putDataPacket(dataPacket.setTask(task));
                        }
                    }
                } else {
                    task.finish(false);
                }
            }
        } catch (Exception e) {
            task.finish(false);
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "TaskRequest[$]出现未捕获的异常", task.getTaskId(), e);
            if (task.getSendView() != null) {
                packetHandler.removeSendView(task.getTaskId());
            }
            if (task.getReceiveView() != null) {
                packetHandler.removeReceiveView(task.getTaskId());
            }
        }
    }
    private void executeTaskResponse(AbstractTask<?> task, DataPacket dataPacket) {
        try {
            if (task.isLive()) {
                boolean finished;
                if (dataPacket == null) {
                    finished = true;
                } else {
                    finished = task.putResponseData(dataPacket);
                }
                if (finished) {
                    taskHashMap.remove(task.getTaskId());
                    if (task.getSendView() != null) {
                        packetHandler.removeSendView(task.getTaskId());
                    }
                    if (task.getReceiveView() != null) {
                        packetHandler.removeReceiveView(task.getTaskId());
                    }
                    task.setLive(false);
                    task.response();
                    task.finish(true);
                    if (task.getPattern() == ImtpsTask.PATTERN_WAIT) {
                        synchronized (task) {
                            task.notifyAll();
                        }
                    }
                }
            }
        } catch (Exception e) {
            task.finish(false);
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "TaskResponse[$]出现未捕获的异常", task.getTaskId(), e);
        }
    }

    public void transmitObject(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
        packetHandler.setThreadPool(threadPool);
    }

    class TaskTimer extends Thread{
        private final PriorityQueue<AbstractTask<?>> WaitingQueue;
        private final Object taskTimerLock = new Object();

        public TaskTimer () {
            WaitingQueue = new PriorityQueue<>(Comparator.comparingLong(AbstractTask::getExpirationTime));
            setDaemon(true);
            start();
        }

        public synchronized void submit(AbstractTask<?> task) {
            task.updateExpirationTime();
            WaitingQueue.add(task);
            AbstractTask<?> oldImtpsTask = WaitingQueue.peek();
            if (task.equals(oldImtpsTask) || oldImtpsTask != null && task.getExpirationTime() < oldImtpsTask.getExpirationTime()) {
                synchronized (taskTimerLock) {
                    taskTimerLock.notify();
                }
            }
        }

        @Override
        public void run() {
            while(true){
                while (!WaitingQueue.isEmpty()){
                    synchronized(taskTimerLock){
                        try {
                            if (WaitingQueue.poll() instanceof AbstractTask<?> task) {
                                long waitTime = task.getExpirationTime() - System.currentTimeMillis();
                                if (waitTime > 0) {
                                    taskTimerLock.wait(waitTime);
                                }
                                threadPool.submit(() -> executeTaskResponse(task, null));
                            }
                        } catch (InterruptedException ignored) {}
                    }
                }
                synchronized (taskTimerLock){
                    try {
                        taskTimerLock.wait();
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }
}
