package com.thezeroer.imtps.server.worker;

import com.thezeroer.imtps.server.log.ImtpsLogger;
import com.thezeroer.imtps.server.session.channel.AcceptChannel;
import com.thezeroer.imtps.server.worker.Inspecter.CountInspecter;
import com.thezeroer.imtps.server.worker.Inspecter.Inspecter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.*;

/**
 * 会话过滤器
 *
 * @author NiZhanBo
 * @since 2025/06/30
 * @version 1.0.0
 */
public class SessionFilter extends Thread {
    private final LinkedBlockingQueue<AcceptChannel> transmitQueue;
    private final ConcurrentHashMap<String, Inspecter> inspecterMap;
    private final Set<Inspecter> inspecterSet;
    private ExecutorService threadPool;
    private boolean live, running;
    private final Object lock = new Object();

    private final SessionHandshaker sessionHandshaker;
    private final SessionManager sessionManager;
    private final ImtpsLogger imtpsLogger;

    public SessionFilter(SessionHandshaker sessionHandshaker, SessionManager sessionManager, ImtpsLogger imtpsLogger) {
        transmitQueue = new LinkedBlockingQueue<>(1024);
        inspecterMap = new ConcurrentHashMap<>();
        inspecterSet = ConcurrentHashMap.newKeySet();
        threadPool = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS
                , new ArrayBlockingQueue<>(12), new ThreadPoolExecutor.CallerRunsPolicy());

        this.sessionHandshaker = sessionHandshaker;
        this.sessionManager = sessionManager;
        this.imtpsLogger = imtpsLogger;
        live = true;
        setName("SessionFilter");
    }

    public void transmit(AcceptChannel acceptChannel) throws InterruptedException {
        imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "连接[$][$]建立", acceptChannel.getType(), acceptChannel.getSocketAddress());
        transmitQueue.put(acceptChannel);
    }

    public void startRunning() {
        running = true;
        if (isAlive()) {
            synchronized (lock) {
                lock.notify();
            }
        } else {
            start();
        }
    }
    public void stopRunning() {
        running = false;
        this.interrupt();
    }
    public void shutdown() {
        live = false;
        running = false;
        this.interrupt();
        for (AcceptChannel acceptChannel : transmitQueue) {
            try {
                acceptChannel.channelClosed();
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void run() {
        while (live) {
            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionFilter StartRunning");
            try {
                while (running) {
                    AcceptChannel acceptChannel = transmitQueue.take();
                    threadPool.submit(() -> {
                        try {
                            if (inspecterSet.stream().allMatch(inspecter -> inspecter.inspect(acceptChannel.getInetAddress()))) {
                                sessionHandshaker.transmit(acceptChannel.setStatus(AcceptChannel.STATUS.Filtered));
                            } else {
                                acceptChannel.getSocketChannel().close();
                            }
                        } catch (Exception ignored) {}
                    });
                }
            } catch (InterruptedException ignored) {}
            if (live) {
                synchronized (lock) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionFilter StopRunning");
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {}
                }
            }
        }
        imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionFilter Shutdown");
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }
    public void registerInspecter(String name, Inspecter inspecter) {
        if (inspecterSet.add(inspecter)) {
            inspecterMap.put(name, inspecter);
            if (inspecter instanceof CountInspecter countInspecter) {
                countInspecter.setSessionManager(sessionManager);
            }
        }
    }
    public void removeInspecter(String name) {
        inspecterSet.remove(inspecterMap.get(name));
    }
    public Inspecter getInspecter(String name) {
        return inspecterMap.get(name);
    }
}
