package imtp.server.listeners;

import imtp.server.link.BaseLinkManager;
import imtp.server.link.FileLinkManager;
import imtp.server.log.ImtpLogger;
import imtp.server.util.NetFilter;
import imtp.server.util.TokenBucket;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 监听器-监听指定端口，等待客户端连接
 *
 * @author NiZhanBo
 * @since 2025/01/22
 * @version 1.0.0
 */
public class Listeners extends Thread{
    private final Selector selector;
    private final ConcurrentLinkedQueue<Runnable> eventQueue;
    private ServerSocketChannel baseServerSocketChannel, fileServerSocketChannel;
    private SelectionKey baseSelectionKey, fileSelectionKey;
    private TokenBucket tokenBucket;
    private NetFilter netFilter;
    private boolean running;
    private final Object lock = new Object();

    private final BaseLinkManager baseLinkManager;
    private final FileLinkManager fileLinkManager;
    private final ImtpLogger imtpLogger;

    public Listeners(BaseLinkManager baseLinkManager, FileLinkManager fileLinkManager, ImtpLogger imtpLogger) throws IOException {
        selector = Selector.open();
        eventQueue = new ConcurrentLinkedQueue<>();
        tokenBucket = new TokenBucket(5000, 1000);
        netFilter = new NetFilter();
        this.baseLinkManager = baseLinkManager;
        this.fileLinkManager = fileLinkManager;
        this.imtpLogger = imtpLogger;
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
                    imtpLogger.log(ImtpLogger.LEVEL_ERROR, "监听器移除baseSelectionKey时出错", e);
                }
                selector.selectedKeys().remove(baseSelectionKey);
                imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "监听器已移除旧baseSelectionKey");
            }
            try {
                baseSelectionKey = this.baseServerSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "监听器已注册新baseServerSocketChannel");
            } catch (ClosedChannelException e) {
                imtpLogger.log(ImtpLogger.LEVEL_ERROR, "监听器注册baseServerSocketChannel时出错", e);
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
                    imtpLogger.log(ImtpLogger.LEVEL_ERROR, "监听器移除fileSelectionKey时出错", e);
                }
                selector.selectedKeys().remove(fileSelectionKey);
                imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "监听器已移除旧fileSelectionKey");
            }
            try {
                fileSelectionKey = this.fileServerSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "监听器已注册新fileServerSocketChannel");
            } catch (ClosedChannelException e) {
                imtpLogger.log(ImtpLogger.LEVEL_ERROR, "监听器注册fileServerSocketChannel时出错", e);
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
            imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "监听器开始运行");
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
                            if (!key.isAcceptable()) {
                                continue;
                            }
                            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                            SocketChannel socketChannel = serverSocketChannel.accept();
                            SocketAddress socketAddress = socketChannel.getRemoteAddress();
                            if (!tokenBucket.acquire()) {
                                imtpLogger.log(ImtpLogger.LEVEL_WARN, "连接 [$] 已建立，流量超标，已关闭", socketAddress);
                                socketChannel.close();
                                continue;
                            }
                            if (!netFilter.verify(socketAddress)) {
                                imtpLogger.log(ImtpLogger.LEVEL_WARN, "连接 [$] 已建立，未通过过滤，已关闭", socketAddress);
                                socketChannel.close();
                                continue;
                            }
                            if (serverSocketChannel.equals(baseServerSocketChannel)) {
                                baseLinkManager.register(socketChannel);
                                imtpLogger.log(ImtpLogger.LEVEL_INFO, "连接 [$] 已建立，添加至 [BaseLink]", socketAddress);
                            } else {
                                fileLinkManager.register(socketChannel);
                                imtpLogger.log(ImtpLogger.LEVEL_INFO, "连接 [$] 已建立，添加至 [FileLink]", socketAddress);
                            }
                        }
                    }
                } catch (IOException e) {
                    imtpLogger.log(ImtpLogger.LEVEL_ERROR, "端口监听器发生异常", e);
                }
            }
            synchronized (lock) {
                imtpLogger.log(ImtpLogger.LEVEL_DEBUG, "监听器停止运行");
                try {
                    lock.wait();
                } catch (InterruptedException ignored) {}
            }
        }
    }
}