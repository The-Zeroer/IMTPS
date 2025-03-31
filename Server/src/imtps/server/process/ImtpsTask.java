package imtps.server.process;

import imtps.server.IMTPS_Server;
import imtps.server.datapacket.DataPacket;
import imtps.server.util.Tool;

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
public abstract class ImtpsTask implements Runnable {
    private static final TaskTimer taskTimer;
    private final String taskId;
    private long overTime = 10000L;
    private volatile DataPacket dataPacket;
    private boolean executeFlag = false;

    private ExecutorService threadPool;
    private ProcessingHub processingHub;
    private IMTPS_Server imtpsServer;

    static {
        taskTimer = new TaskTimer();
        taskTimer.start();
    }

    public ImtpsTask(){
        this.taskId = Tool.produceTaskId();
    }
    public ImtpsTask(String taskId) {
        this.taskId = taskId;
    }

    public abstract boolean request();
    public abstract void response(DataPacket dataPacket);
    public abstract void notResponding();
    public void finish(boolean flag) {}

    public ImtpsTask setOverTime(int seconds) {
        this.overTime = seconds * 1000L;
        return this;
    }
    public void putRequestDataPacket(String UID, DataPacket dataPacket) {
        imtpsServer.putDataPacket(UID, dataPacket.setTaskId(taskId));
    }
    public String getTaskId() {
        return taskId;
    }

    @Override
    public final void run() {
        if (dataPacket != null) {
            response(dataPacket);
        } else {
            processingHub.removeTask(taskId);
            notResponding();
        }
        finish(true);
    }

    void startTime() {
        overTime = System.currentTimeMillis() + overTime;
        taskTimer.submit(this);
    }
    void entrustObject(ExecutorService threadPool, ProcessingHub processingHub, IMTPS_Server imtpsServer) {
        this.threadPool = threadPool;
        this.processingHub = processingHub;
        this.imtpsServer = imtpsServer;
    }
    synchronized void execute(DataPacket dataPacket) {
        if (!executeFlag) {
            executeFlag = true;
            this.dataPacket = dataPacket;
            threadPool.submit(this);
        }
    }

    private static class TaskTimer extends Thread{
        private final PriorityQueue<ImtpsTask> overTimeQueue;
        private final Object taskTimerLock = new Object();
        public TaskTimer () {
            overTimeQueue = new PriorityQueue<>(Comparator.comparingLong(imtpsTask -> imtpsTask.overTime));
        }

        public synchronized void submit(ImtpsTask imtpsTask) {
            overTimeQueue.add(imtpsTask);
            ImtpsTask oldImtpsTask = overTimeQueue.peek();
            if (imtpsTask.equals(oldImtpsTask) || oldImtpsTask != null && imtpsTask.overTime < oldImtpsTask.overTime) {
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
                            if (overTimeQueue.poll() instanceof ImtpsTask imtpsTask) {
                                long waitTime = imtpsTask.overTime - System.currentTimeMillis();
                                if (waitTime > 0) {
                                    taskTimerLock.wait(waitTime);
                                }
                                imtpsTask.execute(null);
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