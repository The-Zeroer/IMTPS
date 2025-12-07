package com.thezeroer.imtps.client.process;

import com.thezeroer.imtps.client.datapacket.DataPacket;
import com.thezeroer.imtps.client.datapacket.PacketCodec;
import com.thezeroer.imtps.client.process.handler.HandlerContext;
import com.thezeroer.imtps.client.process.handler.ImtpsHandler;
import com.thezeroer.imtps.client.process.task.AbstractTask;
import com.thezeroer.imtps.client.worker.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.*;

/**
 * 数据包分发器
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/31
 */
public class DataPacketDispatcher {
    private static final Logger log = LoggerFactory.getLogger(DataPacketDispatcher.class);
    private final HandlerRegistry<ImtpsHandler> handlerRegistry;
    private final ConcurrentLinkedQueue<TaskWrap> taskQueue;
    private final ConcurrentHashMap<String, TaskWrap> taskHashMap;
    private final TaskTimer taskTimer;
    private ExecutorService threadPool;

    private SessionManager sessionManager;
    private final PacketCodec packetCodec;

    public DataPacketDispatcher(PacketCodec packetCodec) {
        handlerRegistry  = new HandlerRegistry<>();
        taskQueue = new ConcurrentLinkedQueue<>();
        taskHashMap = new ConcurrentHashMap<>();
        taskTimer = new TaskTimer();

        int poolSize = Runtime.getRuntime().availableProcessors();
        threadPool = new ThreadPoolExecutor(poolSize, poolSize * 2, 180
                , TimeUnit.SECONDS, new ArrayBlockingQueue<>(1024), new ThreadPoolExecutor.CallerRunsPolicy());

        this.packetCodec = packetCodec;
        packetCodec.setThreadPool(threadPool);
    }

    public void registerHandler(HandlerRegistry.PathsMatcher matcher, ImtpsHandler handler) {
        handlerRegistry.register(matcher, handler);
        if (handler.getName() == null) {
            handler.setName(matcher.formatPath());
        }
    }

    public void handleDataPacket(DataPacket dataPacket) {
        TaskWrap mapTask = taskHashMap.get(dataPacket.getTaskId());
        if (mapTask != null) {
            threadPool.submit(() -> executeTaskResponse(mapTask, dataPacket));
            if (taskQueue.poll() instanceof TaskWrap queuedTask) {
                threadPool.submit(() -> executeTaskRequest(queuedTask));
            }
            return;
        }
        ImtpsHandler handler = handlerRegistry.match(dataPacket.getPaths());
        if (handler == null) {
            log.warn("ImtpHandler{}缺失", dataPacket.getPaths());
            return;
        }
        threadPool.submit(() -> {
           try {
               handler.execute(new HandlerContext(sessionManager, dataPacket));
           } catch (Exception e) {
                log.error("ImtpHandler[{}]出现未捕获的异常", handler.getName(), e);
           }
        });
    }
    public void submitTask(AbstractTask<?> task) {
        TaskWrap taskWrap = new TaskWrap(task);
        switch (task.getPattern()) {
            case AbstractTask.PATTERN_DEFAULT -> threadPool.submit(() -> executeTaskRequest(taskWrap));
            case AbstractTask.PATTERN_QUEUE -> {
                if (taskHashMap.isEmpty()) {
                    threadPool.submit(() -> executeTaskRequest(taskWrap));
                } else {
                    taskQueue.add(taskWrap);
                }
            }
            case AbstractTask.PATTERN_WAIT -> {
                executeTaskRequest(taskWrap);
                synchronized (taskWrap) {
                    try {
                        taskWrap.wait(task.getWaitingTime());
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }
    public void removeTask(String taskId) {
        if (taskHashMap.remove(taskId) instanceof TaskWrap taskWrap && taskWrap.getTask().getPattern() == AbstractTask.PATTERN_QUEUE) {
            taskQueue.remove(taskWrap);
        }
    }
    public void submitTaskToTimer(TaskWrap taskWrap) {
        if (taskWrap != null) {
            taskTimer.submit(taskWrap);
        }
    }

    private void executeTaskRequest(TaskWrap taskWrap) {
        try {
            if (taskWrap.taskRequest() instanceof List<DataPacket> dataPackets && !dataPackets.isEmpty()) {
                taskHashMap.put(taskWrap.getTask().getTaskId(), taskWrap);
                for (DataPacket dataPacket : dataPackets) {
                    sessionManager.putDataPacket(dataPacket.setTaskWrap(taskWrap));
                }
            }
//            taskHashMap.put(task.getTaskId(), task);
//            if (task.getSendView() instanceof AbstractTransmitView sendView) {
//                packetCodec.addSendView(task.getTaskId(), sendView);
//            }
//            if (task.getReceiveView() instanceof AbstractTransmitView receiveView) {
//                packetCodec.addReceiveView(task.getTaskId(), receiveView);
//            }
//            for (DataPacket dataPacket : task.getRequestDataPackets()) {
//                sessionManager.putDataPacket(dataPacket.setTask(task));
//            }
        } catch (Exception e) {
            log.error("TaskRequest[{}]出现未捕获的异常", taskWrap.getTask().getTaskId(), e);
            taskWrap.getTask().done(false);
//            if (task.getSendView() != null) {
//                packetCodec.removeSendView(task.getTaskId());
//            }
//            if (task.getReceiveView() != null) {
//                packetCodec.removeReceiveView(task.getTaskId());
//            }
        }
    }
    private void executeTaskResponse(TaskWrap taskWrap, DataPacket dataPacket) {
        try {
            if (taskWrap.taskResponse(dataPacket)) {
                taskHashMap.remove(taskWrap.getTask().getTaskId());
                if (taskWrap.getTask().getPattern() == AbstractTask.PATTERN_DEFAULT) {
                    taskWrap.notifyAll();
                }
            }
//            if (task.isLive()) {
//                synchronized (task) {
//                    if (task.isLive()) {
//                        boolean finished;
//                        if (dataPacket == null) {
//                            finished = true;
//                        } else {
//                            finished = task.putResponseDataPacket(dataPacket);
//                        }
//                        if (finished) {
//                            taskHashMap.remove(task.getTaskId());
//                            if (task.getSendView() instanceof AbstractTransmitView transmitView) {
//                                packetCodec.removeSendView(task.getTaskId());
//                                transmitView.close();
//                            }
//                            if (task.getReceiveView() instanceof AbstractTransmitView transmitView) {
//                                packetCodec.removeReceiveView(task.getTaskId());
//                                transmitView.close();
//                            }
//                            task.setLive(false);
//                            task.response();
//                            task.finish(true);
//                            if (task.getPattern() == ImtpsTask.PATTERN_WAIT) {
//                                task.notifyAll();
//                            }
//                        }
//                    }
//                }
//            }
        } catch (Exception e) {
            log.error("TaskResponse[{}]出现未捕获的异常", taskWrap.getTask().getTaskId(), e);
            taskWrap.getTask().done(false);
        }
    }

    public void transmitObject(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
        packetCodec.setThreadPool(threadPool);
    }

    class TaskTimer extends Thread {
        private final PriorityQueue<TaskWrap> WaitingQueue;
        private final Object taskTimerLock = new Object();

        public TaskTimer () {
            WaitingQueue = new PriorityQueue<>(Comparator.comparingLong(TaskWrap::getExpirationTime));
            setDaemon(true);
            start();
        }

        public synchronized void submit(TaskWrap taskWrap) {
            taskWrap.updateExpirationTime();
            WaitingQueue.add(taskWrap);
            TaskWrap oldTaskWrap = WaitingQueue.peek();
            if (taskWrap.equals(oldTaskWrap) || oldTaskWrap != null && taskWrap.getExpirationTime() < oldTaskWrap.getExpirationTime()) {
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
                            if (WaitingQueue.poll() instanceof TaskWrap taskWrap) {
                                long waitTime = taskWrap.getExpirationTime() - System.currentTimeMillis();
                                if (waitTime > 0) {
                                    taskTimerLock.wait(waitTime);
                                }
                                threadPool.submit(() -> executeTaskResponse(taskWrap, null));
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
