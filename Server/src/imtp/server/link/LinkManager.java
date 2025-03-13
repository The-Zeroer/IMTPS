package imtp.server.link;

import imtp.server.datapacket.DataPacket;
import imtp.server.datapacket.code.Way;
import imtp.server.datapacket.databody.ByteDataBody;
import imtp.server.log.ImtpLogger;
import imtp.server.process.ProcessingHub;
import imtp.server.security.Secure;

import javax.crypto.*;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * 连接管理器基类
 *
 * @author NiZhanBo
 * @since 2025/01/22
 * @version 1.0.0
 */
public abstract class LinkManager extends Thread {
    protected final String simpleClassName;
    protected final Selector selector;
    protected ExecutorService threadPool;
    protected final ConcurrentLinkedQueue<Runnable> eventQueue;
    protected final ConcurrentHashMap<SelectionKey, SendState> sendStateHashMap;
    protected final ConcurrentHashMap<SelectionKey, Queue<DataPacket>> waitSendHashMap;
    protected int maxLinkCount, currentLinkCount;
    protected boolean running;
    protected final Object lock = new Object();

    protected final HeartBeat heartBeat;
    protected final LinkTable linkTable;
    protected final ProcessingHub processingHub;
    protected final ImtpLogger imtpLogger;
    protected final Secure secure;

    public LinkManager(Secure secure, LinkTable linkTable, ProcessingHub processingHub, ImtpLogger imtpLogger) throws IOException {
        simpleClassName = getClass().getSimpleName();
        selector = Selector.open();
        eventQueue = new ConcurrentLinkedQueue<>();
        waitSendHashMap = new ConcurrentHashMap<>();
        sendStateHashMap = new ConcurrentHashMap<>();
        heartBeat = new HeartBeat();
        maxLinkCount = 10000;
        this.linkTable = linkTable;
        this.processingHub = processingHub;
        this.imtpLogger = imtpLogger;
        this.secure = secure;

        int poolSize = Runtime.getRuntime().availableProcessors();
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1024);
        RejectedExecutionHandler policy = new ThreadPoolExecutor.AbortPolicy();
        threadPool = new ThreadPoolExecutor(poolSize, poolSize * 2, 180, TimeUnit.SECONDS, queue, policy);
    }

    public synchronized void startRunning() {
        running = true;
        if (threadPool.isShutdown()) {
            int poolSize = Runtime.getRuntime().availableProcessors();
            BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1024);
            RejectedExecutionHandler policy = new ThreadPoolExecutor.AbortPolicy();
            threadPool = new ThreadPoolExecutor(poolSize, poolSize * 2, 180, TimeUnit.SECONDS, queue, policy);
        }
        if (isAlive()) {
            synchronized (lock) {
                lock.notifyAll();
            }
        } else {
            start();
        }
        if (!heartBeat.isAlive()) {
            heartBeat.start();
        }
    }
    public synchronized void stopRunning() {
        running = false;
        selector.wakeup();
    }
    public synchronized void shutdown() {
        stopRunning();
        threadPool.shutdown();
        for (SelectionKey selectionKey : selector.keys()) {
            cancel(selectionKey, "LinkManagerShutdown");
        }
    }

    public void setHeartBeatInterval(int interval) {
        heartBeat.setHeartBeatInterval(interval);
    }
    public void setMaxLinkCount(int maxLinkCount) {
        this.maxLinkCount = maxLinkCount;
    }
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public synchronized void register(SocketChannel socketChannel) throws IOException {
        SocketAddress socketAddress = socketChannel.getRemoteAddress();
        if (currentLinkCount < maxLinkCount) {
            int tempLinkCount = ++currentLinkCount;
            socketChannel.configureBlocking(false);
            eventQueue.add(() -> {
                if (socketChannel.isConnected()) {
                    try {
                        SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
                        imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "连接 [$] 已注册至 [$]，当前连接数 [$]", socketAddress, simpleClassName, tempLinkCount);
                        sendStateHashMap.computeIfAbsent(selectionKey, k -> SendState.Leisure);
                        heartBeat.updateLastActivityTime(selectionKey);
                        putDataPacket(selectionKey, new DataPacket(Way.DH_KEY, new ByteDataBody(secure.getPublicKey().getEncoded())));
                    } catch (ClosedChannelException e) {
                        imtpLogger.log(ImtpLogger.LEVEL_ERROR, "连接 [$] 已断开，注册失败 [$]", socketAddress, simpleClassName, e);
                    }
                } else {
                    imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "连接 [$] 已断开，取消注册至 [$]", socketAddress, simpleClassName);
                }
            });
            selector.wakeup();
        } else {
            socketChannel.close();
            imtpLogger.log(ImtpLogger.LEVEL_WARN, "连接数已达到上限 [$]，连接 [$] 已关闭", maxLinkCount, socketAddress);
        }
    }
    public synchronized void cancel(SelectionKey selectionKey, String reason) {
        if (sendStateHashMap.remove(selectionKey) != null) {
            waitSendHashMap.remove(selectionKey);
            heartBeat.removeLastActivityTime(selectionKey);
            int tempLinkCount = --currentLinkCount;
            Runnable runnable = () -> {
                if (selectionKey.isValid()) {
                    try {
                        selectionKey.cancel();
                        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                        SocketAddress socketAddress = socketChannel.getRemoteAddress();
                        socketChannel.close();
                        imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "连接 [$] 已从 [$] 注销，当前连接数 [$] - ($)", socketAddress, simpleClassName, tempLinkCount, reason);
                    } catch (IOException e) {
                        imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "正在从 [$] 注销的连接已被关闭 - ($)", simpleClassName, reason);
                    }
                    extraCancel(selectionKey);
                } else {
                    imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "重复从 [$] 中注销连接 - ($)", simpleClassName, reason);
                }
            };
            if (running) {
                eventQueue.add(runnable);
                selector.wakeup();
            } else {
                runnable.run();
            }
        }
    }

    public void putDataPacket(SelectionKey selectionKey, DataPacket dataPacket) {
        if (selectionKey != null && selectionKey.isValid() && sendStateHashMap.containsKey(selectionKey)) {
            Queue<DataPacket> sendQueue = waitSendHashMap.computeIfAbsent(selectionKey, k -> new ConcurrentLinkedQueue<>());
            sendQueue.add(dataPacket);
            if ((sendStateHashMap.get(selectionKey) != SendState.Writing) && ((selectionKey.interestOps() & SelectionKey.OP_WRITE) == 0)) {
                eventQueue.add(() -> {
                    if (selectionKey.isValid()) {
                        selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                    }
                });
                selector.wakeup();
            }
        }
    }

    @Override
    public void run() {
        while (true) {
            imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "连接管理器 [$] 开始运行", simpleClassName);
            while (running) {
                if (!eventQueue.isEmpty()) {
                    Iterator<Runnable> tasks = eventQueue.iterator();
                    while (tasks.hasNext()) {
                        tasks.next().run();
                        tasks.remove();
                    }
                }
                try {
                    while (selector.select() > 0 && eventQueue.isEmpty()) {
                        Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                        while (selectedKeys.hasNext() && running) {
                            SelectionKey selectionKey = selectedKeys.next();
                            selectedKeys.remove();
                            if (selectionKey.isReadable()) {
                                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_READ);
                                if (sendStateHashMap.get(selectionKey) != SendState.Reading) {
                                    sendStateHashMap.put(selectionKey, SendState.Reading);
                                    receiveDataPacket(selectionKey);
                                    heartBeat.updateLastActivityTime(selectionKey);
                                }
                            } else if (selectionKey.isWritable()) {
                                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
                                Queue<DataPacket> waitSendQueue = waitSendHashMap.get(selectionKey);
                                if (waitSendQueue == null) {
                                    continue;
                                }
                                if (waitSendQueue.isEmpty()) {
                                    waitSendHashMap.remove(selectionKey);
                                    continue;
                                }
                                if (sendStateHashMap.get(selectionKey) != SendState.Writing) {
                                    sendStateHashMap.put(selectionKey, SendState.Writing);
                                    sendDataPacket(selectionKey, waitSendQueue.poll());
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    imtpLogger.log(ImtpLogger.LEVEL_ERROR, "连接管理器 [$] 发生异常", simpleClassName, e);
                }
            }
            synchronized (lock) {
                imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "连接管理器 [$] 停止运行", simpleClassName);
                try {
                    lock.wait();
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private void receiveDataPacket(SelectionKey selectionKey) {
        threadPool.submit(() -> {
            DataPacket dataPacket = new DataPacket();
            try {
                if (!dataPacket.read(selectionKey, processingHub)) {
                    cancel(selectionKey, "客户端主动关闭");
                    return;
                }
            } catch (IOException e) {
                cancel(selectionKey, "接收数据包时连接中断");
                return;
            } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | NoSuchAlgorithmException |
                     InvalidKeyException | ShortBufferException | IllegalBlockSizeException | BadPaddingException e) {
                imtpLogger.log(ImtpLogger.LEVEL_ERROR, "接收数据包时出错", e);
                cancel(selectionKey, "接收数据包时出错");
                return;
            } finally {
                sendStateHashMap.put(selectionKey, SendState.Leisure);
                eventQueue.add(() -> {
                    if (selectionKey.isValid()) {
                        selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
                    }
                });
                selector.wakeup();
            }
            dataPacket.setSelectionKey(selectionKey).setUID(linkTable.getUID(selectionKey));
            imtpLogger.log(ImtpLogger.LEVEL_TRACE, "接收数据包成功 $", dataPacket);
            switch (dataPacket.getWay()) {
                case Way.HEART_BEAT -> {}
                case Way.DH_KEY -> {
                    try {
                        selectionKey.attach(secure.generateKey((byte[]) dataPacket.getDataBodyContent()));
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
                        imtpLogger.log(ImtpLogger.LEVEL_ERROR, "构建AES密钥出错", e);
                    }
                }
                case Way.TOKEN_VERIFY -> {
                    tokenVerify(selectionKey, dataPacket);
                }
                case Way.BUILD_LINK -> {
                    buildLink(selectionKey, dataPacket);
                }
                default -> {
                    processingHub.work(dataPacket, linkTable.getToken(selectionKey) != null);
                }
            }
        });
    }
    private void sendDataPacket(SelectionKey selectionKey, DataPacket dataPacket) {
        threadPool.submit(() -> {
            try {
                dataPacket.write(selectionKey, processingHub.getSendTransferSchedule(dataPacket.getTaskId()));
            } catch (IOException e) {
                cancel(selectionKey, "发送数据包时连接中断");
                return;
            } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | NoSuchAlgorithmException |
                     InvalidKeyException | ShortBufferException | IllegalBlockSizeException | BadPaddingException e) {
                imtpLogger.log(ImtpLogger.LEVEL_ERROR, "发送数据包时出错", e);
                cancel(selectionKey, "发送数据包时出错");
                return;
            } finally {
                sendStateHashMap.put(selectionKey, SendState.Leisure);
                eventQueue.add(() -> {
                    if (selectionKey.isValid()) {
                        selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
                    }
                });
                selector.wakeup();
            }
            dataPacket.setSelectionKey(selectionKey).setUID(linkTable.getUID(selectionKey));
            imtpLogger.log(ImtpLogger.LEVEL_TRACE, "发送数据包成功 $", dataPacket);
        });
    }

    protected abstract void extraCancel(SelectionKey selectionKey);
    protected abstract void tokenVerify(SelectionKey selectionKey, DataPacket dataPacket);
    protected void buildLink(SelectionKey selectionKey, DataPacket dataPacket) {}

    class HeartBeat extends Thread {
        private final ConcurrentHashMap<SelectionKey, Long> lastActivityTime;
        private long HEARTBEAT_INTERVAL = 90000;

        public HeartBeat() {
            lastActivityTime = new ConcurrentHashMap<>();
        }

        public void setHeartBeatInterval(int interval) {
            HEARTBEAT_INTERVAL = interval * 1000L;
        }
        public void updateLastActivityTime(SelectionKey selectionKey) {
            lastActivityTime.put(selectionKey, System.currentTimeMillis());
        }
        public void removeLastActivityTime(SelectionKey selectionKey) {
            lastActivityTime.remove(selectionKey);
        }

        @Override
        public void run() {
            imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "连接管理器 [$] 开启心跳监测，间隔 [$] 秒", simpleClassName, HEARTBEAT_INTERVAL / 1000);
            while (true) {
                try {
                    while (running) {
                        long nowTime = System.currentTimeMillis();
                        for (Map.Entry<SelectionKey, Long> entry : lastActivityTime.entrySet()) {
                            SelectionKey selectionKey = entry.getKey();
                            if (sendStateHashMap.get(selectionKey) == SendState.Leisure && nowTime - entry.getValue() > HEARTBEAT_INTERVAL) {
                                if (selectionKey.channel().isOpen()) {
                                    cancel(selectionKey, "心跳超时");
                                } else {
                                    lastActivityTime.remove(selectionKey);
                                }
                            }
                        }
                        long waitTime = HEARTBEAT_INTERVAL - (System.currentTimeMillis() - nowTime);
                        if (waitTime > 0) {
                            synchronized (lock) {
                                lock.wait(waitTime);
                            }
                        }
                    }
                    synchronized (lock) {
                        lock.wait();
                    }
                } catch (InterruptedException ignored) {}
            }
        }
    }

    enum SendState {
        Reading, Writing, Leisure
    }
}