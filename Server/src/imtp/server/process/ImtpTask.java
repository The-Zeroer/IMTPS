package imtp.server.process;

import imtp.server.datapacket.DataPacket;
import imtp.server.util.Tool;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;

/**
 * 任务
 *
 * @author NiZhanBo
 * @since 2025/03/01
 * @version 1.0.0
 */
public abstract class ImtpTask implements Runnable {
    private static final TaskTimer taskTimer;
    private final String taskId;
    private long overTime = 10000L;
    private volatile DataPacket dataPacket;
    private boolean executeFlag = false;

    private ExecutorService threadPool;
    private ProcessingHub processingHub;

    static {
        taskTimer = new TaskTimer();
        taskTimer.start();
    }

    public ImtpTask(){
        this.taskId = Tool.produceTaskId();
    }
    public ImtpTask(String taskId) {
        this.taskId = taskId;
    }

    public ImtpTask setOverTime(int seconds) {
        this.overTime = seconds * 1000L;
        return this;
    }
    public void startTime() {
        overTime = System.currentTimeMillis() + overTime;
        taskTimer.submit(this);
    }
    public abstract void request();
    public abstract void response();
    public abstract void notResponding();

    @Override
    public final void run() {
        if (dataPacket != null) {
            response();
        } else {
            processingHub.removeTask(taskId);
            notResponding();
        }
    }
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }
    public void setProcessingHub(ProcessingHub processingHub) {
        this.processingHub = processingHub;
    }
    public String getTaskId() {
        return taskId;
    }
    public synchronized void execute(DataPacket dataPacket) {
        if (!executeFlag) {
            executeFlag = true;
            this.dataPacket = dataPacket;
            threadPool.submit(this);
        }
    }

    static class TaskTimer extends Thread{
        private final PriorityQueue<ImtpTask> overTimeQueue;
        private final Object taskTimerLock = new Object();
        public TaskTimer () {
            overTimeQueue = new PriorityQueue<>(Comparator.comparingLong(imtpTask -> imtpTask.overTime));
        }

        public void submit(ImtpTask imtpTask) {
            ImtpTask oldImtpTask = overTimeQueue.peek();
            overTimeQueue.add(imtpTask);
            if (oldImtpTask == null || imtpTask.overTime < oldImtpTask.overTime) {
                synchronized (taskTimerLock) {
                    taskTimerLock.notify();
                }
            }
        }

        @Override
        public void run() {
            while(true){
                while (!overTimeQueue.isEmpty()){
                    synchronized(taskTimerLock){
                        try {
                            if (overTimeQueue.peek() instanceof ImtpTask imtpTask) {
                                long waitTime = imtpTask.overTime - System.currentTimeMillis();
                                if (waitTime > 0) {
                                    taskTimerLock.wait(waitTime);
                                }
                                imtpTask.execute(null);
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