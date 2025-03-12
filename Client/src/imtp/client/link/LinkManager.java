package imtp.client.link;

import imtp.client.datapacket.DataPacket;
import imtp.client.datapacket.code.Type;
import imtp.client.datapacket.code.Way;
import imtp.client.datapacket.databody.ByteDataBody;
import imtp.client.datapacket.databody.TextDataBody;
import imtp.client.log.ImtpLogger;
import imtp.client.process.ProcessingHub;
import imtp.client.security.Secure;

import javax.crypto.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * 连接管理器
 *
 * @author NiZhanBo
 * @since 2025/01/22
 * @version 1.0.0
 */
public class LinkManager extends Thread {
    private final Selector selector;
    private ExecutorService threadPool;
    private final ConcurrentLinkedQueue<Runnable> eventQueue;
    private final ConcurrentHashMap<SelectionKey, SendState> sendStateHashMap;
    private final ConcurrentHashMap<SelectionKey, Queue<DataPacket>> waitSendHashMap;
    private boolean running;
    private InetSocketAddress serverAddress;
    private final Object lock = new Object();

    private final HeartBeat heartBeat;
    private final LinkTable linkTable;
    private final ProcessingHub processingHub;
    private final ImtpLogger imtpLogger;
    private final Secure secure;

    public LinkManager(Secure secure, LinkTable linkTable, ProcessingHub processingHub, ImtpLogger imtpLogger) throws IOException {
        selector = Selector.open();
        eventQueue = new ConcurrentLinkedQueue<>();
        waitSendHashMap = new ConcurrentHashMap<>();
        sendStateHashMap = new ConcurrentHashMap<>();
        heartBeat = new HeartBeat();
        this.linkTable = linkTable;
        this.processingHub = processingHub;
        this.imtpLogger = imtpLogger;
        this.secure = secure;

        int poolSize = Runtime.getRuntime().availableProcessors();
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(16);
        RejectedExecutionHandler policy = new ThreadPoolExecutor.AbortPolicy();
        threadPool = new ThreadPoolExecutor(2, poolSize, 180, TimeUnit.SECONDS, queue, policy);
    }

    public synchronized void startRunning() {
        running = true;
        if (threadPool.isShutdown()) {
            int poolSize = Runtime.getRuntime().availableProcessors();
            BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(16);
            RejectedExecutionHandler policy = new ThreadPoolExecutor.AbortPolicy();
            threadPool = new ThreadPoolExecutor(2, poolSize, 180, TimeUnit.SECONDS, queue, policy);
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
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public void register(SocketChannel socketChannel, String linkName) throws IOException {
        socketChannel.configureBlocking(false);
        SocketAddress socketAddress = socketChannel.getRemoteAddress();
        final Object registerLock = new Object();
        Runnable runnable  = () -> {
            if (socketChannel.isConnected()) {
                try {
                    SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
                    sendStateHashMap.computeIfAbsent(selectionKey, k -> SendState.Leisure);
                    if ("baseLink".equals(linkName)) {
                        linkTable.setBaseSelectionKey(selectionKey);
                        heartBeat.setBaseSelectionKey(selectionKey);
                        serverAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
                    } else {
                        linkTable.setFileSelectionKey(selectionKey);
                    }
                    imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "连接 ($) [$] 已注册", linkName, socketAddress);
                    putDataPacket(selectionKey, new DataPacket(Way.DH_KEY, new ByteDataBody(secure.getPublicKey().getEncoded())));
                } catch (IOException e) {
                    imtpLogger.log(ImtpLogger.LEVEL_ERROR, "注册连接 [$] 时出错", linkName, e);
                }
            }
            synchronized (registerLock) {
                registerLock.notify();
            }
        };
        eventQueue.add(runnable);
        if (running) {
            selector.wakeup();
            synchronized (registerLock) {
                try {
                    registerLock.wait(3000);
                } catch (InterruptedException ignored) {}
            }
        }
    }
    public synchronized void cancel(SelectionKey selectionKey, String reason) {
        if (sendStateHashMap.remove(selectionKey) != null) {
            waitSendHashMap.remove(selectionKey);
            Runnable runnable = () -> {
                if (selectionKey.isValid()) {
                    try {
                        selectionKey.cancel();
                        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                        SocketAddress socketAddress = socketChannel.getRemoteAddress();
                        socketChannel.close();
                        imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "连接 [$] 已注销 - ($)", socketAddress, reason);
                    } catch (IOException e) {
                        imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "正在注销的连接已被关闭 - ($)", reason);
                    }
                } else {
                    imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "重复注销连接 - ($)", reason);
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
    public synchronized void againLink() {
        if (sendStateHashMap.containsKey(linkTable.getBaseSelectionKey())) {
            cancel(linkTable.getBaseSelectionKey(), "重新构建连接");
        }
        for (int i = 1; i <= 5; i++) {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException ignored) {}
            imtpLogger.log(ImtpLogger.LEVEL_INFO, "第[$]次尝试重新连接服务器", i);
            try {
                SocketChannel socketChannel = SocketChannel.open(serverAddress);
                imtpLogger.log(ImtpLogger.LEVEL_INFO, "重新连接服务器成功");
                register(socketChannel, "baseLink");
                putDataPacket(linkTable.getBaseSelectionKey(), new DataPacket(Way.TOKEN_VERIFY, new TextDataBody(linkTable.getToken())));
                break;
            } catch (IOException ignored) {}
        }
    }

    public boolean putDataPacket(SelectionKey selectionKey, DataPacket dataPacket) {
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
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void run() {
        while (true) {
            imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "连接管理器开始运行");
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
                                    heartBeat.upDateLastActivityTime();
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    imtpLogger.log(ImtpLogger.LEVEL_ERROR, "连接管理器发生异常", e);
                }
            }
            synchronized (lock) {
                imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "连接管理器停止运行");
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
                if (!dataPacket.read(selectionKey)) {
                    cancel(selectionKey, "服务端主动关闭");
                    return;
                }
            } catch (IOException e) {
                cancel(selectionKey, "接收数据包时连接中断");
                if (selectionKey.equals(heartBeat.baseSelectionKey)) {
                    againLink();
                }
                return;
            } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | NoSuchAlgorithmException |
                     InvalidKeyException | ShortBufferException | IllegalBlockSizeException | BadPaddingException e) {
                imtpLogger.log(ImtpLogger.LEVEL_ERROR, "接收数据包时AES出错", e);
                cancel(selectionKey, "接收数据包时AES出错");
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
            dataPacket.setSelectionKey(selectionKey);
            imtpLogger.log(ImtpLogger.LEVEL_TRACE, "接收数据包成功 $", dataPacket);
            switch (dataPacket.getWay()) {
                case Way.DH_KEY -> {
                    try {
                        selectionKey.attach(secure.generateKey((byte[]) dataPacket.getDataBodyContent()));
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
                        imtpLogger.log(ImtpLogger.LEVEL_ERROR, "构建AES密钥出错", e);
                    }
                }
                case Way.BUILD_LINK -> {
                    if (dataPacket.getType() == Type.FILE_LINK) {
                        try {
                            String[] fileAddress = ((String) dataPacket.getDataBodyContent()).split(":");
                            SocketChannel fileLinkChannel = SocketChannel.open(new InetSocketAddress(fileAddress[0], Integer.parseInt(fileAddress[1])));
                            register(fileLinkChannel, "fileLink");
                            SelectionKey fileSelectionKey = linkTable.getFileSelectionKey();
                            if (fileSelectionKey != null) {
                                putDataPacket(fileSelectionKey, new DataPacket(Way.TOKEN_VERIFY, new TextDataBody(linkTable.getToken())));
                                imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "FileLink已向服务器提交Token");
                                for (DataPacket cacheDataPacket : linkTable.getCacheDataPacketQueue()) {
                                    putDataPacket(fileSelectionKey, cacheDataPacket);
                                }
                                linkTable.setFileLinkState(LinkTable.LINKSTATE_READY);
                            }
                        } catch (Exception e) {
                            imtpLogger.log(ImtpLogger.LEVEL_ERROR, "建立FileLink时出错", e);
                        }
                    } else {
                        processingHub.work(dataPacket);
                    }
                }
                case Way.TOKEN_VERIFY -> {
                    linkTable.setToken((String) dataPacket.getDataBodyContent());
                    imtpLogger.log(ImtpLogger.LEVEL_INFO, "收到Token");
                }
                default -> {
                    processingHub.work(dataPacket);
                }
            }
        });
    }
    private void sendDataPacket(SelectionKey selectionKey, DataPacket dataPacket) {
        threadPool.submit(() -> {
            try {
                dataPacket.write(selectionKey);
            } catch (IOException e) {
                cancel(selectionKey, "发送数据包时连接中断");
                return;
            } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | ShortBufferException |
                     IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException | InvalidKeyException e) {
                imtpLogger.log(ImtpLogger.LEVEL_ERROR, "发送数据包时AES出错", e);
                cancel(selectionKey, "发送数据包时AES出错");
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
            imtpLogger.log(ImtpLogger.LEVEL_TRACE, "发送数据包成功 $", dataPacket);
        });
    }

    class HeartBeat extends Thread {
        private long HEARTBEAT_INTERVAL = 30000L;
        private long lastActivityTime;
        private SelectionKey baseSelectionKey;

        public void setBaseSelectionKey(SelectionKey baseSelectionKey) {
            this.baseSelectionKey = baseSelectionKey;
        }
        public void setHeartBeatInterval(int interval) {
            HEARTBEAT_INTERVAL = interval * 1000L;
        }
        public void upDateLastActivityTime() {
            lastActivityTime = System.currentTimeMillis();
        }

        @Override
        public void run() {
            imtpLogger.log(ImtpLogger.LEVEL_INFO, "连接管理器开启心跳发送，间隔 [$] 秒", HEARTBEAT_INTERVAL / 1000);
            while (true) {
                try {
                    while (running) {
                        long nowTime = System.currentTimeMillis();
                        if (nowTime - lastActivityTime > HEARTBEAT_INTERVAL) {
                            putDataPacket(baseSelectionKey, new DataPacket(Way.HEART_BEAT));
                        }
                        synchronized (lock) {
                            lock.wait(HEARTBEAT_INTERVAL);
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