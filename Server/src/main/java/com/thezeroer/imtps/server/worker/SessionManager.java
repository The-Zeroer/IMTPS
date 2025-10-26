package com.thezeroer.imtps.server.worker;

import com.thezeroer.imtps.server.address.AddressManager;
import com.thezeroer.imtps.server.datapacket.ControlPacket;
import com.thezeroer.imtps.server.datapacket.DataPacket;
import com.thezeroer.imtps.server.datapacket.PacketHandler;
import com.thezeroer.imtps.server.log.ImtpsLogger;
import com.thezeroer.imtps.server.process.ProcessingHub;
import com.thezeroer.imtps.server.session.ImtpsSession;
import com.thezeroer.imtps.server.session.channel.AcceptChannel;
import com.thezeroer.imtps.server.session.channel.ImtpsChannel;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * 会话管理器
 *
 * @author NiZhanBo
 * @since 2025/06/29
 * @version 1.0.0
 */
public class SessionManager extends Thread {
    private final Selector selector;
    private final LinkedBlockingQueue<AcceptChannel> transmitQueue;
    private final ConcurrentLinkedQueue<Runnable> eventQueue;
    private final ConcurrentHashMap<String, ImtpsSession> idToSessionHashMap;
    private final ConcurrentHashMap<String, ImtpsSession> nameToSessionHashMap;
    private final Map<String, AcceptChannel> verifyHashMap;
    private ExecutorService threadPool;
    private boolean live, running;
    private final HeartBeat heartBeat;
    private final Object lock = new Object();

    private final EnumMap<ImtpsChannel.TYPE, SelectorWorker> selectorWorkerMapping;
    private final PacketHandler packetHandler;
    private final ProcessingHub processingHub;
    private final AddressManager addressManager;
    private final ImtpsLogger imtpsLogger;

    public SessionManager(PacketHandler packetHandler, ProcessingHub processingHub, AddressManager addressManager, ImtpsLogger imtpsLogger) throws IOException {
        selector = Selector.open();
        transmitQueue = new LinkedBlockingQueue<>(1024);
        eventQueue = new ConcurrentLinkedQueue<>();
        idToSessionHashMap = new ConcurrentHashMap<>();
        nameToSessionHashMap = new ConcurrentHashMap<>();
        selectorWorkerMapping = new EnumMap<>(ImtpsChannel.TYPE.class){{
            put(ImtpsChannel.TYPE.DataBasic, new SelectorWorker(ImtpsChannel.TYPE.DataBasic));
            put(ImtpsChannel.TYPE.DataFile, new SelectorWorker(ImtpsChannel.TYPE.DataFile));
        }};
        verifyHashMap = Collections.synchronizedMap(new LinkedHashMap<>(1024) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, AcceptChannel> eldest) {
                return size() > 4096;
            }
        });
        int poolSize = Runtime.getRuntime().availableProcessors();
        threadPool = new ThreadPoolExecutor(poolSize, poolSize * 2, 180
                , TimeUnit.SECONDS, new ArrayBlockingQueue<>(1024), new ThreadPoolExecutor.CallerRunsPolicy());
        this.heartBeat = new HeartBeat();

        this.packetHandler = packetHandler;
        this.processingHub = processingHub;
        this.addressManager = addressManager;
        this.imtpsLogger = imtpsLogger;
        live = true;
        setName("SessionManager");
    }

    public void transmit(AcceptChannel acceptChannel) throws InterruptedException {
        transmitQueue.put(acceptChannel);
        selector.wakeup();
    }

    public void startRunning() {
        running = true;
        if (isAlive()) {
            synchronized (lock) {
                lock.notifyAll();
            }
        } else {
            start();
            for (Thread thread : selectorWorkerMapping.values()) {
                thread.start();
            }
            heartBeat.start();
        }
    }
    public void stopRunning() {
        running = false;
        selector.wakeup();
        for (SelectorWorker selectorWorker : selectorWorkerMapping.values()) {
            selectorWorker.selector.wakeup();
        }
    }
    public void shutdown() {
        live = false;
        running = false;
        selector.wakeup();
        for (SelectorWorker selectorWorker : selectorWorkerMapping.values()) {
            selectorWorker.selector.wakeup();
        }
        for (AcceptChannel acceptChannel : transmitQueue) {
            try {
                acceptChannel.channelClosed();
            } catch (IOException ignored) {}
        }
        for (ImtpsSession imtpsSession : idToSessionHashMap.values()) {
            try {
                imtpsSession.channelClosed(ImtpsChannel.TYPE.Control);
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void run() {
        while (live) {
            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionManager StartRunning");
            while (running) {
                try {
                    if (!transmitQueue.isEmpty()) {
                        Iterator<AcceptChannel> iterator = transmitQueue.iterator();
                        while (iterator.hasNext()) {
                            AcceptChannel acceptChannel = iterator.next(); iterator.remove();
                            SelectionKey selectionKey = acceptChannel.getSocketChannel().register(selector, SelectionKey.OP_READ);
                            ImtpsSession imtpsSession = new ImtpsSession(selectionKey, acceptChannel.getImtpsSecretKey(), acceptChannel.getString());
                            selectionKey.attach(imtpsSession);
                            idToSessionHashMap.put(acceptChannel.getString(), imtpsSession);
                            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionManager[ProtocolControl]已注册连接[$]", acceptChannel.getSocketAddress());
                        }
                    }
                    while (!eventQueue.isEmpty()) {
                        Runnable task = eventQueue.poll();
                        if (task != null) {
                            task.run();
                        }
                    }
                    while (selector.select(10000) > 0) {
                        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                        while (keys.hasNext() && running) {
                            SelectionKey selectionKey = keys.next(); keys.remove();
                            if (selectionKey.isReadable()) {
                                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_READ);
                                readEvent(selectionKey);
                            } else if (selectionKey.isWritable()) {
                                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
                                writeEvent(selectionKey);
                            }
                        }
                    }
                } catch (Exception e) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionManager[ProtocolControl] AriseError", e);
                }
            }
            if (live) {
                synchronized (lock) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionManager StopRunning");
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {}
                }
            }
        }
        imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionManager Shutdown");
    }
    private void readEvent(SelectionKey selectionKey) {
        ImtpsSession imtpsSession = (ImtpsSession) selectionKey.attachment();
        if (imtpsSession.getControlChannel().updateLastActivityTime().isReading()) {
            return;
        } else {
            imtpsSession.getControlChannel().setReading(true);
        }
        threadPool.submit(() -> {
            ControlPacket controlPacket;
            try {
                controlPacket = packetHandler.readControlPacket(selectionKey, imtpsSession.getImtpsSecretKey());
                if (controlPacket == null) {
                    closeChannel(imtpsSession, ImtpsChannel.TYPE.Control, "接收ControlPacket为空");
                    return;
                }
            } catch (Exception e) {
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionManager-ReadEvent", e);
                closeChannel(imtpsSession, ImtpsChannel.TYPE.Control, "接收ControlPacket出错");
                return;
            } finally {
                imtpsSession.getControlChannel().setReading(false);
                if (imtpsSession.getControlChannel().getStatus() == ImtpsChannel.STATUS.Connected) {
                    eventQueue.add(() -> {
                        if (selectionKey.isValid()) {
                            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
                        }
                    });
                    selector.wakeup();
                }
            }
            imtpsLogger.trace("接收ControlPacket[$]", controlPacket);
            try {
                handleControlPacket(imtpsSession, controlPacket);
            } catch (InterruptedException e) {
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionManager-HandleControlPacket AriseError", e);
            }
        });
    }
    private void writeEvent(SelectionKey selectionKey) {
        ImtpsSession imtpsSession = (ImtpsSession) selectionKey.attachment();
        if (imtpsSession.getControlChannel().isWriting()) {
            return;
        } else {
            imtpsSession.getControlChannel().setWriting(true);
        }
        threadPool.submit(() -> {
            try {
                ConcurrentLinkedQueue<ControlPacket> controlPacketQueue = imtpsSession.getControlChannel().getSendQueue();
                while (!controlPacketQueue.isEmpty()) {
                    packetHandler.writeControlPacket(selectionKey, imtpsSession.getImtpsSecretKey(), Objects.requireNonNull(controlPacketQueue.poll()));
                }
            } catch (Exception e) {
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionManager-WriteEvent", e);
                closeChannel(imtpsSession, ImtpsChannel.TYPE.Control, "发送ControlPacket出错");
            } finally {
                imtpsSession.getControlChannel().setWriting(false);
                if (imtpsSession.getControlChannel().getStatus() == ImtpsChannel.STATUS.Connected) {
                    if (selectionKey.isValid() && !imtpsSession.getControlChannel().getSendQueue().isEmpty()) {
                        eventQueue.add(() -> {
                            if (selectionKey.isValid()) {
                                selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                            }
                        });
                        selector.wakeup();
                    }
                }
            }
        });
    }

    public void putDataPacket(ImtpsSession imtpsSession, DataPacket dataPacket) {
        imtpsSession.putSendQueue(dataPacket);
        selectorWorkerMapping.get(ImtpsChannel.chooseType(dataPacket.getDataBodyType())).addWriteEvent(imtpsSession);
    }
    private void putControlPacket(SelectionKey selectionKey, ControlPacket controlPacket) {
        ((ImtpsSession) selectionKey.attachment()).getControlChannel().getSendQueue().add(controlPacket);
        if (!((ImtpsSession) selectionKey.attachment()).getControlChannel().isWriting() && selectionKey.isValid() && ((selectionKey.interestOps() & SelectionKey.OP_WRITE) == 0)) {
            eventQueue.add(() -> {
                if (selectionKey.isValid()) {
                    selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                }
            });
            selector.wakeup();
        }
    }

    private void handleControlPacket(ImtpsSession imtpsSession, ControlPacket controlPacket) throws InterruptedException {
        switch (controlPacket.getWay()) {
            case ControlPacket.WAY.HEART_BEAT -> imtpsSession.updateLastActivityTime(ImtpsChannel.TYPE.Control);
            case ControlPacket.WAY.TOKEN -> {
                if (verifyHashMap.get(new String(controlPacket.getContent(), StandardCharsets.UTF_8)) instanceof AcceptChannel acceptChannel) {
                    selectorWorkerMapping.get(acceptChannel.getType()).transmit(acceptChannel.setString(imtpsSession.getSessionId()));
                }
            }
            case ControlPacket.WAY.PORT_DATA_BASIC -> putControlPacket(imtpsSession.getControlChannel().getSelectionKey(),
                    new ControlPacket(ControlPacket.WAY.PORT_DATA_BASIC, String.valueOf(addressManager.choose
                            (imtpsSession.getRemoteAddress(), ImtpsChannel.TYPE.DataBasic)).getBytes(StandardCharsets.UTF_8)));
            case ControlPacket.WAY.PORT_DATA_FILE -> putControlPacket(imtpsSession.getControlChannel().getSelectionKey(),
                    new ControlPacket(ControlPacket.WAY.PORT_DATA_FILE, String.valueOf(addressManager.choose
                            (imtpsSession.getRemoteAddress(), ImtpsChannel.TYPE.DataFile)).getBytes(StandardCharsets.UTF_8)));
        }
    }

    public Map<String, AcceptChannel> getVerifyMap() {
        return verifyHashMap;
    }
    public ConcurrentHashMap<String, ImtpsSession> getNameToSessionHashMap() {
        return nameToSessionHashMap;
    }

    public void closeChannel(ImtpsSession imtpsSession, ImtpsChannel.TYPE type, String reason) {
        try {
            imtpsSession.channelClosed(type);
            if (type == ImtpsChannel.TYPE.Control) {
                idToSessionHashMap.remove(imtpsSession.getSessionId());
                if (imtpsSession.getSessionName() instanceof String sessionName) {
                    nameToSessionHashMap.remove(sessionName);
                }
            }
            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "关闭通道[$][$][$]", type, imtpsSession.getChannel(type).getSocketAddress(), reason);
        } catch (Exception e) {
            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "关闭通道[$][$][$]出错", type, imtpsSession.getChannel(type).getSocketAddress(), reason, e);}
    }

    public void setThreadPool(ImtpsChannel.TYPE type, ExecutorService threadPool) {
        if (type == ImtpsChannel.TYPE.Control) {
            this.threadPool = threadPool;
        } else {
            selectorWorkerMapping.get(type).setThreadPool(threadPool);
        }
    }
    public void setHeartBeatInterval(ImtpsChannel.TYPE type, int interval) {
        heartBeat.setHeartBeatRunnable(type, interval);
    }

    public int getSelectionKeyCount() {
        int count = selector.keys().size();
        for (SelectorWorker selectorWorker : selectorWorkerMapping.values()) {
            count += selectorWorker.getSelectionKeyCount();
        }
        return count;
    }

    class SelectorWorker extends Thread {
        private final ImtpsChannel.TYPE type;
        private final Selector selector;
        private final LinkedBlockingQueue<AcceptChannel> transmitQueue;
        private final ConcurrentLinkedQueue<Runnable> eventQueue;
        private ExecutorService threadPool;

        protected SelectorWorker(ImtpsChannel.TYPE type) throws IOException {
            this.type = type;
            selector = Selector.open();
            transmitQueue = new LinkedBlockingQueue<>(1024);
            eventQueue = new ConcurrentLinkedQueue<>();
            int poolSize = Runtime.getRuntime().availableProcessors();
            threadPool = new ThreadPoolExecutor(poolSize, poolSize * 2, 180
                    , TimeUnit.SECONDS, new ArrayBlockingQueue<>(1024), new ThreadPoolExecutor.CallerRunsPolicy());
            setName("SessionManager[SelectorWorker" + type.name() + "]");
        }

        protected void transmit(AcceptChannel acceptChannel) throws InterruptedException {
            transmitQueue.put(acceptChannel);
            selector.wakeup();
        }

        @Override
        public void run() {
            while (live) {
                while (running) {
                    try {
                        if (!transmitQueue.isEmpty()) {
                            Iterator<AcceptChannel> iterator = transmitQueue.iterator();
                            while (iterator.hasNext()) {
                                AcceptChannel acceptChannel = iterator.next();iterator.remove();
                                if (idToSessionHashMap.get(acceptChannel.getString()) instanceof ImtpsSession imtpsSession) {
                                    SelectionKey selectionKey = acceptChannel.getSocketChannel().register(selector, SelectionKey.OP_READ);
                                    imtpsSession.getDataChannel(type).setSelectionKey(selectionKey);
                                    selectionKey.attach(imtpsSession);
                                    imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionManager[$]已注册连接[$]", type.name(), acceptChannel.getSocketAddress());
                                    switch (type) {
                                        case DataBasic -> putControlPacket(imtpsSession.getControlChannel().getSelectionKey(),
                                                new ControlPacket(ControlPacket.WAY.READY_DATA_BASIC));
                                        case DataFile -> putControlPacket(imtpsSession.getControlChannel().getSelectionKey(),
                                                new ControlPacket(ControlPacket.WAY.READY_DATA_FILE));
                                    }
                                    if (!imtpsSession.getDataChannel(type).getSendQueue().isEmpty()) {
                                        selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                                    }
                                } else {
                                    imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "错误的SessionId来自连接[$]", acceptChannel.getSocketAddress());
                                    acceptChannel.getSocketChannel().close();
                                }
                            }
                        }
                        while (!eventQueue.isEmpty()) {
                            Runnable task = eventQueue.poll();
                            if (task != null) {
                                task.run();
                            }
                        }
                        while (selector.select(10000) > 0) {
                            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                            while (keys.hasNext() && running) {
                                SelectionKey selectionKey = keys.next(); keys.remove();
                                if (selectionKey.isReadable()) {
                                    selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_READ);
                                    readEvent(selectionKey);
                                } else if (selectionKey.isWritable()) {
                                    selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
                                    writeEvent(selectionKey);
                                }
                            }
                        }
                    } catch (Exception e) {
                        imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionManager[$] AriseError", type.name(), e);
                    }
                }
                if (live) {
                    synchronized (lock) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ignored) {}
                    }
                }
            }
        }
        private void readEvent(SelectionKey selectionKey) {
            ImtpsSession imtpsSession = (ImtpsSession) selectionKey.attachment();
            if (imtpsSession.getDataChannel(type).updateLastActivityTime().isReading()) {
                return;
            } else {
                imtpsSession.getDataChannel(type).setReading(true);
            }
            threadPool.submit(() -> {
                DataPacket dataPacket;
                try {
                    dataPacket= packetHandler.readDataPacket(selectionKey, imtpsSession.getImtpsSecretKey());
                    if (dataPacket == null) {
                        closeChannel(imtpsSession, type, "接收DataPacket为空");
                        return;
                    }
                } catch (Exception e) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionManager[$]-ReadEvent AriseError", type, e);
                    closeChannel(imtpsSession, type, "接收DataPacket出错");
                    return;
                } finally {
                    imtpsSession.getDataChannel(type).setReading(false);
                    if (imtpsSession.getDataChannel(type).getStatus() == ImtpsChannel.STATUS.Connected) {
                        eventQueue.add(() -> {
                            if (selectionKey.isValid()) {
                                selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
                            }
                        });
                        selector.wakeup();
                    }
                }
                imtpsLogger.trace("接收DataPacket[$]", dataPacket);
                processingHub.handleDataPacket(dataPacket, (ImtpsSession) selectionKey.attachment());
            });
        }
        private void writeEvent(SelectionKey selectionKey) {
            ImtpsSession imtpsSession = (ImtpsSession) selectionKey.attachment();
            if (imtpsSession.getDataChannel(type).isWriting()) {
                return;
            } else {
                imtpsSession.getDataChannel(type).setWriting(true);
            }
            threadPool.submit(() -> {
                try {
                    ConcurrentLinkedQueue<DataPacket> dataPacketQueue = imtpsSession.getDataChannel(type).getSendQueue();
                    while (!dataPacketQueue.isEmpty()) {
                        if (dataPacketQueue.poll() instanceof DataPacket dataPacket) {
                            packetHandler.writeDataPacket(selectionKey, imtpsSession.getImtpsSecretKey(), dataPacket);
                            processingHub.submitTaskToTimer(dataPacket.getTask());
                        }
                    }
                } catch (Exception e) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionManager[$]-WriteEvent AriseError", type, e);
                    closeChannel(imtpsSession, type, "发送DataPacket出错");
                } finally {
                    imtpsSession.getDataChannel(type).setWriting(false);
                    if (imtpsSession.getDataChannel(type).getStatus() == ImtpsChannel.STATUS.Connected) {
                        if (!imtpsSession.getDataChannel(type).getSendQueue().isEmpty()) {
                            eventQueue.add(() -> {
                                if (selectionKey.isValid()) {
                                    selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                                }
                            });
                            selector.wakeup();
                        }
                    }
                }
            });
        }

        public void addWriteEvent(ImtpsSession imtpsSession) {
            if (imtpsSession.getDataChannel(type).getStatus() == ImtpsChannel.STATUS.Unconnected) {
                switch (type) {
                    case ImtpsChannel.TYPE.DataBasic -> putControlPacket(imtpsSession.getControlChannel().getSelectionKey(),
                            new ControlPacket(ControlPacket.WAY.PORT_DATA_BASIC, String.valueOf(addressManager.choose
                                    (imtpsSession.getRemoteAddress(), ImtpsChannel.TYPE.DataBasic)).getBytes(StandardCharsets.UTF_8)));
                    case ImtpsChannel.TYPE.DataFile -> putControlPacket(imtpsSession.getControlChannel().getSelectionKey(),
                            new ControlPacket(ControlPacket.WAY.PORT_DATA_FILE, String.valueOf(addressManager.choose
                                    (imtpsSession.getRemoteAddress(), ImtpsChannel.TYPE.DataFile)).getBytes(StandardCharsets.UTF_8)));
                }
            } else {
                SelectionKey selectionKey = imtpsSession.getDataChannel(type).getSelectionKey();
                if (selectionKey != null && !imtpsSession.getDataChannel(type).isWriting() && ((selectionKey.interestOps() & SelectionKey.OP_WRITE) == 0)) {
                    eventQueue.add(() -> {
                        if (selectionKey.isValid()) {
                            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                        }
                    });
                    selector.wakeup();
                }
            }
        }

        public void setThreadPool(ExecutorService threadPool) {
            this.threadPool = threadPool;
        }
        public int getSelectionKeyCount() {
            return selector.keys().size();
        }
    }

    class HeartBeat extends Thread {
        private final ConcurrentHashMap<ImtpsChannel.TYPE, HeartBeatRunnable> heartBeatHashMap;
        private final PriorityQueue<HeartBeatRunnable> heartBeatPriorityQueue;

        public HeartBeat() {
            heartBeatHashMap = new ConcurrentHashMap<>();
            heartBeatPriorityQueue = new PriorityQueue<>();
            setName("HeartBeat");
            setDaemon(true);
        }

        public void setHeartBeatRunnable(ImtpsChannel.TYPE type, int interval) {
            synchronized (heartBeatPriorityQueue) {
                if (heartBeatHashMap.remove(type) instanceof HeartBeatRunnable heartBeatRunnable) {
                    heartBeatPriorityQueue.remove(heartBeatRunnable);
                }
                HeartBeatRunnable heartBeatRunnable = new HeartBeatRunnable(type, interval);
                heartBeatHashMap.put(type, heartBeatRunnable);
                heartBeatPriorityQueue.offer(heartBeatRunnable);
                heartBeatPriorityQueue.notifyAll();
            }
            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "心跳验证间隔[$][$]秒", type, interval);
        }

        @Override
        public void run() {
            if (!heartBeatHashMap.containsKey(ImtpsChannel.TYPE.Control)) {
                setHeartBeatRunnable(ImtpsChannel.TYPE.Control, 60);
            }
            if (!heartBeatHashMap.containsKey(ImtpsChannel.TYPE.DataBasic)) {
                setHeartBeatRunnable(ImtpsChannel.TYPE.DataBasic, 300);
            }
            if (!heartBeatHashMap.containsKey(ImtpsChannel.TYPE.DataFile)) {
                setHeartBeatRunnable(ImtpsChannel.TYPE.DataFile, 30);
            }
            while (live) {
                imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "HeartBeat StartRunning");
                try {
                    while (running) {
                        HeartBeatRunnable heartBeatRunnable;
                        synchronized (heartBeatPriorityQueue) {
                            while (heartBeatPriorityQueue.isEmpty()) {
                                heartBeatPriorityQueue.wait();
                            }
                            long nowTime = System.currentTimeMillis();
                            heartBeatRunnable = heartBeatPriorityQueue.poll();
                            if (heartBeatRunnable == null) {
                                continue;
                            }
                            if (heartBeatRunnable.nextRunTime > nowTime) {
                                heartBeatPriorityQueue.wait(heartBeatRunnable.nextRunTime - nowTime);
                            }
                        }
                        heartBeatRunnable.run();
                        synchronized (heartBeatPriorityQueue) {
                            heartBeatPriorityQueue.offer(heartBeatRunnable);
                        }
                    }
                    if (live) {
                        synchronized (lock) {
                            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "HeartBeat Stopping");
                            lock.wait();
                        }
                    }
                } catch (InterruptedException ignored) {
                } catch (Exception e) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "HeartBeat AriseError", e);
                }
            }
            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "HeartBeat Shutdown");
        }

        class HeartBeatRunnable implements Runnable, Comparable<HeartBeatRunnable> {
            private long interval, nextRunTime;
            private final ImtpsChannel.TYPE type;

            public HeartBeatRunnable(ImtpsChannel.TYPE type, int interval) {
                this.type = type;
                setHeartBeatInterval(interval);
            }

            public void setHeartBeatInterval(int interval) {
                this.interval = interval * 1000L;
                nextRunTime = interval + System.currentTimeMillis();
            }

            @Override
            public void run() {
                long nowTime = System.currentTimeMillis();
                nextRunTime = interval + nowTime;
                for (ImtpsSession imtpsSession : idToSessionHashMap.values()) {
                    ImtpsChannel imtpsChannel = imtpsSession.getChannel(type);
                    if (imtpsChannel.getStatus() == ImtpsChannel.STATUS.Connected && imtpsChannel.getLastActivityTime() + interval < nowTime
                            && !imtpsChannel.isReading() && !imtpsChannel.isWriting()) {
                        closeChannel(imtpsSession, type, "心跳超时");
                    }
                }
            }

            @Override
            public int compareTo(HeartBeatRunnable o) {
                return Long.compare(this.nextRunTime, o.nextRunTime);
            }
        }
    }
}
