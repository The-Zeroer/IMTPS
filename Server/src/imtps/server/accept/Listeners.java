package imtps.server.accept;

import imtps.server.link.BaseLinkManager;
import imtps.server.link.FileLinkManager;
import imtps.server.link.LinkManager;
import imtps.server.log.ImtpsLogger;
import imtps.server.security.SecureManager;
import imtps.server.util.NetFilter;
import imtps.server.util.TokenBucket;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.*;

/**
 * 监听器-监听指定端口，等待客户端连接
 *
 * @author NiZhanBo
 * @since 2025/01/22
 * @version 1.0.0
 */
public class Listeners extends Thread{
    private final Selector selector;
    private final ExecutorService threadPool;
    private final ConcurrentLinkedQueue<Runnable> eventQueue;
    private ServerSocketChannel baseServerSocketChannel, fileServerSocketChannel;
    private SelectionKey baseSelectionKey, fileSelectionKey;
    private TokenBucket tokenBucket;
    private NetFilter netFilter;
    private final HandShakeTimer handShakeTimer;
    private boolean running;
    private final Object lock = new Object();

    private final BaseLinkManager baseLinkManager;
    private final FileLinkManager fileLinkManager;
    private final SecureManager secureManager;
    private final ImtpsLogger imtpsLogger;

    public Listeners(SecureManager secureManager, BaseLinkManager baseLinkManager, FileLinkManager fileLinkManager, ImtpsLogger imtpsLogger) throws IOException {
        selector = Selector.open();
        eventQueue = new ConcurrentLinkedQueue<>();
        tokenBucket = new TokenBucket(3000, 1000);
        netFilter = new NetFilter();
        handShakeTimer = new HandShakeTimer();
        this.secureManager = secureManager;
        this.baseLinkManager = baseLinkManager;
        this.fileLinkManager = fileLinkManager;
        this.imtpsLogger = imtpsLogger;

        threadPool = new ThreadPoolExecutor(4, Runtime.getRuntime().availableProcessors(), 180
                , TimeUnit.SECONDS, new ArrayBlockingQueue<>(1024), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public void setBaseServerSocketChannel(ServerSocketChannel baseServerSocketChannel) throws IOException {
        this.baseServerSocketChannel = baseServerSocketChannel;
        this.baseServerSocketChannel.configureBlocking(false);
        eventQueue.add(() -> {
            if (baseSelectionKey != null) {
                baseSelectionKey.cancel();
                try {
                    baseSelectionKey.channel().close();
                } catch (IOException e) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "监听器移除baseSelectionKey时出错", e);
                }
                selector.selectedKeys().remove(baseSelectionKey);
                imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "监听器已移除旧baseSelectionKey");
            }
            try {
                baseSelectionKey = this.baseServerSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "监听器已注册新baseServerSocketChannel");
            } catch (ClosedChannelException e) {
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "监听器注册baseServerSocketChannel时出错", e);
            }
        });
        if (running) {
            selector.wakeup();
        }
    }
    public void setFileServerSocketChannel(ServerSocketChannel fileServerSocketChannel) throws IOException {
        this.fileServerSocketChannel = fileServerSocketChannel;
        this.fileServerSocketChannel.configureBlocking(false);
        eventQueue.add(() -> {
            if (fileSelectionKey != null) {
                fileSelectionKey.cancel();
                try {
                    fileSelectionKey.channel().close();
                } catch (IOException e) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "监听器移除fileSelectionKey时出错", e);
                }
                selector.selectedKeys().remove(fileSelectionKey);
                imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "监听器已移除旧fileSelectionKey");
            }
            try {
                fileSelectionKey = this.fileServerSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "监听器已注册新fileServerSocketChannel");
            } catch (ClosedChannelException e) {
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "监听器注册fileServerSocketChannel时出错", e);
            }
        });
        if (running) {
            selector.wakeup();
        }
    }
    public ServerSocketChannel getBaseServerSocketChannel() {
        return baseServerSocketChannel;
    }
    public ServerSocketChannel getFileServerSocketChannel() {
        return fileServerSocketChannel;
    }

    public void setTokenBucket(TokenBucket tokenBucket) {
        this.tokenBucket = tokenBucket;
    }
    public void setNetFilter(NetFilter netFilter) {
        this.netFilter = netFilter;
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
        selector.wakeup();
    }
    public void shutdown() {
        running = false;
        if (baseSelectionKey != null) {
            baseSelectionKey.cancel();
            baseSelectionKey = null;
        }
        if (fileSelectionKey != null) {
            fileSelectionKey.cancel();
            fileSelectionKey = null;
        }
        if (baseServerSocketChannel != null) {
            try {
                baseServerSocketChannel.close();
            } catch (IOException ignored) {}
            baseServerSocketChannel = null;
        }
        if (fileServerSocketChannel != null) {
            try {
                fileServerSocketChannel.close();
            } catch (IOException ignored) {}
            fileServerSocketChannel = null;
        }
        selector.wakeup();
    }

    @Override
    public void run(){
        while (true) {
            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "监听器开始运行");
            while (running) {
                while (!eventQueue.isEmpty()) {
                    Runnable task = eventQueue.poll();
                    task.run();
                }
                try {
                    while (selector.select() > 0) {
                        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                        while (keys.hasNext() && running) {
                            SelectionKey key = keys.next();
                            keys.remove();
                            if (key.isAcceptable()) {
                                ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                                handleSocketChannel(serverSocketChannel.accept(), serverSocketChannel.equals(baseServerSocketChannel));
                            }
                        }
                    }
                } catch (IOException e) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "端口监听器发生异常", e);
                }
            }
            synchronized (lock) {
                imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "监听器停止运行");
                try {
                    lock.wait();
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private void handleSocketChannel(SocketChannel socketChannel, boolean isBaseLink) {
        handShakeTimer.submit(new HandShakeEvent(threadPool.submit(() -> {
            try {
                SocketAddress socketAddress = socketChannel.getRemoteAddress();
                if (!tokenBucket.acquire()) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "连接 [$] 已建立，流量超标，已关闭", socketAddress);
                    socketChannel.close();
                    return;
                }
                if (!netFilter.verify(socketAddress)) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "连接 [$] 已建立，未通过过滤，已关闭", socketAddress);
                    socketChannel.close();
                    return;
                }
                if (isBaseLink) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "连接 [$] 已建立，正在添加至 [BaseLinkManager]", socketAddress);
                    handShake(socketChannel, baseLinkManager);
                } else {
                    imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "连接 [$] 已建立，正在添加至 [FileLinkManager]", socketAddress);
                    handShake(socketChannel, fileLinkManager);
                }
            } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "握手失败", e);
            }
        }), socketChannel));
    }

    public void handShake(SocketChannel socketChannel, LinkManager linkManager)
            throws IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        byte[] keyBytes = secureManager.getPublicKey().getEncoded();
        ByteBuffer byteBuffer = ByteBuffer.allocate(2 + keyBytes.length).putShort((short) keyBytes.length).put(keyBytes);
        socketChannel.write(byteBuffer.flip());

        byteBuffer = ByteBuffer.allocate(2);
        socketChannel.read(byteBuffer);
        byteBuffer = ByteBuffer.allocate(byteBuffer.flip().getShort());
        socketChannel.read(byteBuffer);
        SecretKey secretKey = secureManager.generateKey(byteBuffer.flip().array());

        byte[] finishedMessage = SecureManager.generateFinishedMessage(secretKey);
        byteBuffer = ByteBuffer.allocate(2 + finishedMessage.length).putShort((short) finishedMessage.length).put(finishedMessage);
        socketChannel.write(byteBuffer.flip());

        byteBuffer = ByteBuffer.allocate(2);
        socketChannel.read(byteBuffer);
        byteBuffer = ByteBuffer.allocate(byteBuffer.flip().getShort());
        socketChannel.read(byteBuffer);
        if (Arrays.equals(finishedMessage, byteBuffer.array())) {
            linkManager.register(socketChannel, secretKey);
        } else {
            imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "握手失败，finishedMessage错误");
        }
    }

    static class HandShakeEvent {
        private final Future<?> future;
        private final SocketChannel socketChannel;
        private final long expirationTime;

        public HandShakeEvent(Future<?> future, SocketChannel socketChannel) {
            this.future = future;
            this.socketChannel = socketChannel;
            expirationTime = System.currentTimeMillis() + 1000;
        }
    }

    class HandShakeTimer extends Thread{
        private final Queue<HandShakeEvent> handShakeEventQueue;
        private final Object HandShakeTimerLock = new Object();
        public HandShakeTimer() {
            handShakeEventQueue = new LinkedList<>();
            start();
        }

        public synchronized void submit(HandShakeEvent handShakeEvent) {
            handShakeEventQueue.add(handShakeEvent);
            synchronized (HandShakeTimerLock) {
                HandShakeTimerLock.notify();
            }
        }

        @Override
        public void run() {
            while(true){
                while (!handShakeEventQueue.isEmpty()){
                    synchronized(HandShakeTimerLock){
                        try {
                            HandShakeEvent handShakeEvent = handShakeEventQueue.poll();
                            if (handShakeEvent != null) {
                                long waitTime = handShakeEvent.expirationTime - System.currentTimeMillis();
                                if (waitTime > 0) {
                                    HandShakeTimerLock.wait(waitTime);
                                }
                                if (!handShakeEvent.future.isDone()) {
                                    imtpsLogger.log(ImtpsLogger.LEVEL_WARN, "握手超时，连接 [$] 已关闭", handShakeEvent.socketChannel.getRemoteAddress());
                                    handShakeEvent.socketChannel.close();
                                }
                            }
                        } catch (InterruptedException | IOException ignored) {}
                    }
                }
                synchronized (HandShakeTimerLock){
                    try {
                        HandShakeTimerLock.wait();
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }


}