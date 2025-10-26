package com.thezeroer.imtps.client.worker;

import com.thezeroer.imtps.client.address.AddressManager;
import com.thezeroer.imtps.client.datapacket.ControlPacket;
import com.thezeroer.imtps.client.datapacket.DataPacket;
import com.thezeroer.imtps.client.datapacket.PacketHandler;
import com.thezeroer.imtps.client.event.ImtpsEventCatch;
import com.thezeroer.imtps.client.log.ImtpsLogger;
import com.thezeroer.imtps.client.process.ProcessingHub;
import com.thezeroer.imtps.client.session.ImtpsSession;
import com.thezeroer.imtps.client.session.channel.AcceptChannel;
import com.thezeroer.imtps.client.session.channel.DataChannel;
import com.thezeroer.imtps.client.session.channel.ImtpsChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * 会话管理器
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/05
 */
public class SessionManager extends Thread {
    private volatile ImtpsSession imtpsSession;
    private final Selector selector;
    private final ConcurrentLinkedQueue<Runnable> eventQueue;
    private final ConcurrentHashMap<ImtpsChannel.TYPE, InetSocketAddress> addressMap;
    private ExecutorService threadPool;
    private boolean live, running;
    private final HeartBeat heartBeat;
    private final Object lock = new Object();

    private SessionHandshaker sessionHandshaker;
    private final PacketHandler packetHandler;
    private final ProcessingHub processingHub;
    private final AddressManager addressManager;
    private final ImtpsLogger imtpsLogger;
    private ImtpsEventCatch imtpsEventCatch;

    public SessionManager(PacketHandler packetHandler, ProcessingHub processingHub, AddressManager addressManager, ImtpsLogger imtpsLogger) throws IOException {
        selector = Selector.open();
        eventQueue = new ConcurrentLinkedQueue<>();
        addressMap = new ConcurrentHashMap<>();
        threadPool = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS
                , new ArrayBlockingQueue<>(12), new ThreadPoolExecutor.CallerRunsPolicy());
        heartBeat = new HeartBeat();
        imtpsEventCatch = new ImtpsEventCatch() {};

        this.packetHandler = packetHandler;
        this.processingHub = processingHub;
        this.addressManager = addressManager;
        this.imtpsLogger = imtpsLogger;
        live = true;
        setName("SessionManager");
    }

    public void transmit(AcceptChannel acceptChannel, boolean wait) {
        CountDownLatch latch = new CountDownLatch(1);
        Runnable runnable = () -> {
            try {
                SelectionKey selectionKey = acceptChannel.getSocketChannel().configureBlocking(false)
                        .register(selector, SelectionKey.OP_READ);
                if (acceptChannel.getType() == ImtpsChannel.TYPE.Control) {
                    imtpsSession = new ImtpsSession(selectionKey, acceptChannel.getImtpsSecretKey(), acceptChannel.getString());
                } else {
                    imtpsSession.getDataChannel(acceptChannel.getType()).setSelectionKey(selectionKey);
                    putControlPacket(new ControlPacket(ControlPacket.WAY.TOKEN, acceptChannel.getString().getBytes(StandardCharsets.UTF_8)));
                }
                selectionKey.attach(acceptChannel.getType());
                addressMap.put(acceptChannel.getType(), acceptChannel.getSocketAddress());
                imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "通道[$]注册完成", acceptChannel.getType());
            } catch (Exception e) {
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "通道[$]注册出错", acceptChannel.getType(), e);
            } finally {
                latch.countDown();
            }
        };
        if (running) {
            eventQueue.add(runnable);
            selector.wakeup();
            if (wait) {
                try {
                    latch.await();
                } catch (InterruptedException ignored) {}
            }
        } else {
            runnable.run();
        }
    }

    public void startRunning() {
        running = true;
        if (isAlive()) {
            synchronized (lock) {
                lock.notify();
            }
        } else {
            start();
            heartBeat.start();
        }
    }
    public void stopRunning() {
        running = false;
        selector.wakeup();
    }
    public void shutdown() {
        live = false;
        running = false;
        selector.wakeup();
        if (imtpsSession != null) {
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
                    while (!eventQueue.isEmpty()) {
                        eventQueue.poll().run();
                    }
                    while (selector.select(5000) > 0) {
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
                    imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionManager AriseError", e);
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
        ImtpsChannel.TYPE type = (ImtpsChannel.TYPE) selectionKey.attachment();
        if (imtpsSession.getChannel(type).isReading()) {
            return;
        } else {
            imtpsSession.getChannel(type).setReading(true);
        }
        threadPool.submit(() -> {
            Object packet;
            if (imtpsSession.getChannel(type).getStatus() != ImtpsChannel.STATUS.Connected) {
                return;
            }
            try {
                if (type == ImtpsChannel.TYPE.Control) {
                    packet = packetHandler.readControlPacket(selectionKey, imtpsSession.getImtpsSecretKey());
                    if (packet == null) {
                        closeChannel(selectionKey);
                        imtpsEventCatch.serverClose(true);
                        return;
                    }
                } else {
                    packet = packetHandler.readDataPacket(selectionKey, imtpsSession.getImtpsSecretKey());
                    if (packet == null) {
                        closeChannel(selectionKey);
                        return;
                    }
                }
            } catch (Exception e) {
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionManager-ReadEvent AriseError", e);
                closeChannel(selectionKey);
                imtpsEventCatch.readBreak();
                if (type == ImtpsChannel.TYPE.Control) {
                    imtpsEventCatch.serverClose(false);
                }
                if (e instanceof SocketException) {
                    reconnection((ImtpsChannel.TYPE) selectionKey.attachment());
                }
                return;
            } finally {
                imtpsSession.getChannel(type).setReading(false);
                if (imtpsSession.getChannel(type).getStatus() == ImtpsChannel.STATUS.Connected) {
                    eventQueue.add(() -> {
                        if (selectionKey.isValid()) {
                            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
                        }
                    });
                    selector.wakeup();
                }
            }
            if (type == ImtpsChannel.TYPE.Control) {
                imtpsLogger.trace("接收ControlPacket[$]", packet);
                try {
                    handleControlPacket((ControlPacket) packet);
                } catch (Exception e) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionManager-HandleControlPacket AriseError", e);
                }
            } else {
                imtpsLogger.trace("接收DataPacket[$]", packet);
                processingHub.handleDataPacket((DataPacket) packet);
            }
        });
    }
    private void writeEvent(SelectionKey selectionKey) {
        ImtpsChannel.TYPE type = (ImtpsChannel.TYPE) selectionKey.attachment();
        if (imtpsSession.getChannel(type).isWriting()) {
            return;
        } else {
            imtpsSession.getChannel(type).setWriting(true);
        }
        threadPool.submit(() -> {
            try {
                if (type == ImtpsChannel.TYPE.Control) {
                    ConcurrentLinkedQueue<ControlPacket> controlPacketQueue = imtpsSession.getControlChannel().getSendQueue();
                    while (!controlPacketQueue.isEmpty()) {
                        imtpsSession.updateLastActivityTime();
                        packetHandler.writeControlPacket(selectionKey, imtpsSession.getImtpsSecretKey(), Objects.requireNonNull(controlPacketQueue.poll()));
                    }
                } else {
                    ConcurrentLinkedQueue<DataPacket> dataPacketQueue = imtpsSession.getDataChannel(type).getSendQueue();
                    while (!dataPacketQueue.isEmpty()) {
                        if (dataPacketQueue.poll() instanceof DataPacket dataPacket) {
                            packetHandler.writeDataPacket(selectionKey, imtpsSession.getImtpsSecretKey(), dataPacket);
                            processingHub.submitTaskToTimer(dataPacket.getTask());
                        }
                    }
                }
            } catch (Exception e) {
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionManager-WriteEvent AriseError", e);
                closeChannel(selectionKey);
                imtpsEventCatch.writeBreak();
                if (e instanceof SocketException) {
                    reconnection((ImtpsChannel.TYPE) selectionKey.attachment());
                }
            } finally {
                imtpsSession.getChannel(type).setWriting(false);
                if (imtpsSession.getChannel(type).getStatus() == ImtpsChannel.STATUS.Connected) {
                    if (!imtpsSession.getChannel(type).getSendQueue().isEmpty()) {
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

    public void putDataPacket(DataPacket dataPacket) {
        imtpsSession.putSendQueue(dataPacket);
        DataChannel dataChannel = imtpsSession.getDataChannel(ImtpsChannel.chooseType(dataPacket.getDataBodyType()));
        if (dataChannel.getStatus() == DataChannel.STATUS.Unconnected) {
            synchronized (lock) {
                if (dataChannel.getStatus() == DataChannel.STATUS.Unconnected) {
                    switch (dataPacket.getDataBodyType()) {
                        case Basic -> putControlPacket(new ControlPacket(ControlPacket.WAY.PORT_DATA_BASIC));
                        case File -> putControlPacket(new ControlPacket(ControlPacket.WAY.PORT_DATA_FILE));
                    }
                    dataChannel.setStatus(DataChannel.STATUS.Connecting);
                }
            }
        } else {
            if (!dataChannel.isWriting() && dataChannel.getStatus() == DataChannel.STATUS.Connected) {
                SelectionKey selectionKey = dataChannel.getSelectionKey();
                if (selectionKey != null && selectionKey.isValid() && ((selectionKey.interestOps() & SelectionKey.OP_WRITE) == 0)) {
                    eventQueue.add(() -> {
                        if (selectionKey.isValid()) {
                            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                        }
                    });
                    selector.wakeup();
                }
            }
        }
    }
    private void putControlPacket(ControlPacket controlPacket) {
        imtpsSession.getControlChannel().getSendQueue().add(controlPacket);
        SelectionKey selectionKey = imtpsSession.getControlChannel().getSelectionKey();
        if (!imtpsSession.getControlChannel().isWriting() && selectionKey.isValid() && ((selectionKey.interestOps() & SelectionKey.OP_WRITE) == 0)) {
            eventQueue.add(() -> {
                if (selectionKey.isValid()) {
                    selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                }
            });
            selector.wakeup();
        }
    }

    public ImtpsSession getImtpsSession() {
        return imtpsSession;
    }
    public void transmitObject(SessionHandshaker sessionHandshaker) {
        this.sessionHandshaker = sessionHandshaker;
    }

    private void handleControlPacket(ControlPacket controlPacket) throws Exception {
        switch (controlPacket.getWay()) {
            case ControlPacket.WAY.PORT_DATA_BASIC -> {
                sessionHandshaker.transmit(new AcceptChannel(SocketChannel.open(new InetSocketAddress(addressManager.getServerHostName(),
                        Integer.parseInt(new String(controlPacket.getContent(), StandardCharsets.UTF_8)))), ImtpsChannel.TYPE.DataBasic));
            }
            case ControlPacket.WAY.PORT_DATA_FILE -> {
                sessionHandshaker.transmit(new AcceptChannel(SocketChannel.open(new InetSocketAddress(addressManager.getServerHostName(),
                        Integer.parseInt(new String(controlPacket.getContent(), StandardCharsets.UTF_8)))), ImtpsChannel.TYPE.DataFile));
            }
            case ControlPacket.WAY.READY_DATA_BASIC -> {
                SelectionKey selectionKey = imtpsSession.getDataChannel(ImtpsChannel.TYPE.DataBasic).getSelectionKey();
                eventQueue.add(() -> {
                    if (selectionKey.isValid()) {
                        selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                    }
                });
                selector.wakeup();
            }
            case ControlPacket.WAY.READY_DATA_FILE -> {
                SelectionKey selectionKey = imtpsSession.getDataChannel(ImtpsChannel.TYPE.DataFile).getSelectionKey();
                eventQueue.add(() -> {
                    if (selectionKey.isValid()) {
                        selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                    }
                });
                selector.wakeup();
            }
        }
    }
    private void closeChannel(SelectionKey selectionKey) {
        try {
            imtpsSession.channelClosed((ImtpsChannel.TYPE) selectionKey.attachment());
        } catch (Exception ignored) {}
    }
    private void reconnection(ImtpsChannel.TYPE type) {
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "与服务端的连接中断[$]，正在尝试重新连接", type);
        if (type != ImtpsChannel.TYPE.Control) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
        }
        synchronized (this) {
            if (imtpsSession.getChannel(type).getStatus() == ImtpsChannel.STATUS.Unconnected) {
                for (int i = 1; i <= 5; i++) {
                    try {
                        Thread.sleep(1000 * (i + 2));
                    } catch (InterruptedException ignored) {}
                    try {
                        sessionHandshaker.transmit(new AcceptChannel(SocketChannel.open(addressMap.get(type)), type));
                        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "第[$]次重新连接服务器成功[$]", i, type);
                        imtpsEventCatch.reconnection(true);
                        break;
                    } catch (Exception e) {
                        imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "第[$]次重新连接服务器失败[$]", i, type);
                    }
                    imtpsEventCatch.reconnection(false);
                }
            }
        }
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }
    public void setImtpsEventCatch(ImtpsEventCatch imtpsEventCatch) {
        this.imtpsEventCatch = imtpsEventCatch;
    }
    public void setHeartBeatInterval(int heartBeatInterval) {
        heartBeat.setHeartBeatInterval(heartBeatInterval);
    }

    class HeartBeat extends Thread {
        private long HEARTBEAT_INTERVAL = 20000L;

        public HeartBeat() {
            setName("HeartBeat");
            setDaemon(true);
        }

        public void setHeartBeatInterval(int interval) {
            HEARTBEAT_INTERVAL = interval * 1000L;
        }

        @Override
        public void run() {
            while (live) {
                imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "HeartBeat StartRunning，间隔[$]秒", HEARTBEAT_INTERVAL / 1000);
                try {
                    while (running) {
                        if (imtpsSession != null) {
                            if (System.currentTimeMillis() - imtpsSession.getLastActivityTime() > HEARTBEAT_INTERVAL) {
                                putControlPacket(new ControlPacket(ControlPacket.WAY.HEART_BEAT));
                            }
                        }
                        synchronized (lock) {
                            lock.wait(HEARTBEAT_INTERVAL);
                        }
                    }
                    if (live) {
                        synchronized (lock) {
                            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "HeartBeat Stopping");
                            lock.wait();
                        }
                    }
                } catch (InterruptedException ignored) {}
            }
            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "HeartBeat Shutdown");
        }
    }
}
