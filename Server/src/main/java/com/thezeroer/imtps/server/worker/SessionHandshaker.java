package com.thezeroer.imtps.server.worker;

import com.thezeroer.imtps.server.buffer.BufferManager;
import com.thezeroer.imtps.server.log.ImtpsLogger;
import com.thezeroer.imtps.server.security.ImtpsSecretKey;
import com.thezeroer.imtps.server.session.ImtpsSession;
import com.thezeroer.imtps.server.session.SessionUtil;
import com.thezeroer.imtps.server.session.channel.AcceptChannel;
import com.thezeroer.imtps.server.session.channel.ImtpsChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话握手器
 *
 * @author NiZhanBo
 * @since 2025/07/03
 * @version 1.0.0
 */
public class SessionHandshaker extends Thread {
    private final Selector selector;
    private final LinkedBlockingQueue<AcceptChannel> transmitQueue;
    private final Map<String, AcceptChannel> verifyMap;
    private ExecutorService threadPool;
    private boolean live, running;
    private final Object lock = new Object();

    private final SessionManager sessionManager;
    private final ImtpsLogger imtpsLogger;

    public SessionHandshaker(SessionManager sessionManager, ImtpsLogger imtpsLogger) throws IOException {
        selector = Selector.open();
        transmitQueue = new LinkedBlockingQueue<>(1024);
        verifyMap = sessionManager.getVerifyMap();
        threadPool = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS
                , new ArrayBlockingQueue<>(12), new ThreadPoolExecutor.CallerRunsPolicy());

        this.sessionManager = sessionManager;
        this.imtpsLogger = imtpsLogger;
        live = true;
        setName("SessionHandshaker");
    }

    public void transmit(AcceptChannel acceptChannel) throws InterruptedException {
        transmitQueue.put(acceptChannel);
        selector.wakeup();
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
        live = false;
        running = false;
        selector.wakeup();
        for (AcceptChannel acceptChannel : transmitQueue) {
            try {
                acceptChannel.channelClosed();
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void run() {
        while (live) {
            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionHandshaker StartRunning");
            while (running) {
                try {
                    if (!transmitQueue.isEmpty()) {
                        Iterator<AcceptChannel> iterator = transmitQueue.iterator();
                        while (iterator.hasNext()) {
                            AcceptChannel acceptChannel = iterator.next(); iterator.remove();
                            if (acceptChannel.getType() == ImtpsChannel.TYPE.Control) {
                                acceptChannel.getSocketChannel().configureBlocking(false).register
                                        (selector, SelectionKey.OP_READ).attach(acceptChannel);
                            } else {
                                acceptChannel.getSocketChannel().configureBlocking(false).register
                                        (selector, SelectionKey.OP_WRITE).attach(acceptChannel);
                            }
                        }
                    }
                    while (selector.select(1000) > 0) {
                        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                        while (keys.hasNext() && running) {
                            SelectionKey key = keys.next(); keys.remove();
                            key.interestOps(0);
                            if (key.isReadable()) {
                                readEvent(key);
                            } else if (key.isWritable()) {
                                writeEvent(key);
                            }
                        }
                    }
                } catch (Exception e) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionHandshaker AriseError", e);
                }
            }
            if (live) {
                synchronized (lock) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionHandshaker StopRunning");
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {}
                }
            }
        }
        imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionHandshaker Shutdown");
    }
    private void readEvent(SelectionKey selectionKey) {
        threadPool.submit(() -> {
            AcceptChannel acceptChannel = (AcceptChannel) selectionKey.attachment();
            BufferManager bufferManager = BufferManager.get();
            try {
                switch (acceptChannel.getStatus()) {
                    case Filtered -> {
                        ByteBuffer byteBuffer = bufferManager.getSrcBuffer(ImtpsSecretKey.DH_LENGTH);
                        while (byteBuffer.hasRemaining()) {
                            if (acceptChannel.getSocketChannel().read(byteBuffer) == -1) {
                                closeSelectionKey(selectionKey);
                                return;
                            }
                        }
                        KeyPair keyPair = ImtpsSecretKey.createKeyPair();
                        byte[] publicKey = new byte[ImtpsSecretKey.DH_LENGTH];
                        byteBuffer.flip().get(publicKey);
                        ImtpsSecretKey imtpsSecretKey = new ImtpsSecretKey(keyPair.getPrivate(), publicKey);
                        byteBuffer.clear().put(keyPair.getPublic().getEncoded()).flip();
                        while (byteBuffer.hasRemaining()) {
                            acceptChannel.getSocketChannel().write(byteBuffer);
                        }
                        acceptChannel.setImtpsSecretKey(imtpsSecretKey).setStatus(AcceptChannel.STATUS.Handshaking);
                    }
                    case Handshaking -> {
                        ByteBuffer byteBuffer = bufferManager.getSrcBuffer(ImtpsSecretKey.FINISHEDMESSAGE_LENGTH);
                        while (byteBuffer.hasRemaining()) {
                            if (acceptChannel.getSocketChannel().read(byteBuffer) == -1) {
                                closeSelectionKey(selectionKey);
                                return;
                            }
                        }
                        byte[] serverMessage = acceptChannel.getImtpsSecretKey().getFinishedMessage();
                        byte[] clientMessage = new byte[ImtpsSecretKey.FINISHEDMESSAGE_LENGTH];
                        byteBuffer.flip().get(clientMessage);
                        if (Arrays.equals(serverMessage, clientMessage)) {
                            String sessionId = SessionUtil.creationSessionId();
                            byteBuffer = bufferManager.getSrcBuffer(ImtpsSecretKey.FINISHEDMESSAGE_LENGTH + ImtpsSecretKey.NONCE_LENGTH + ImtpsSession.SESSIONID_LENGTH + ImtpsSecretKey.TAG_LENGTH)
                                    .put(serverMessage).put(acceptChannel.getImtpsSecretKey().encrypt(sessionId.getBytes(StandardCharsets.UTF_8))).flip();
                            while (byteBuffer.hasRemaining()) {
                                acceptChannel.getSocketChannel().write(byteBuffer);
                            }
                            selectionKey.cancel();
                            sessionManager.transmit(acceptChannel.setString(sessionId).setStatus(AcceptChannel.STATUS.Handshaked));
                        } else {
                            closeSelectionKey(selectionKey);
                        }
                    }
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                closeSelectionKey(selectionKey);
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionHandshaker[ThreadPool] AriseError", e);
            } finally {
                if (selectionKey.isValid()) {
                    selectionKey.interestOps(SelectionKey.OP_READ);
                    selector.wakeup();
                }
            }
        });
    }
    private void writeEvent(SelectionKey selectionKey) {
        threadPool.submit(() -> {
            AcceptChannel acceptChannel = (AcceptChannel) selectionKey.attachment();
            BufferManager bufferManager = BufferManager.get();
            ByteBuffer byteBuffer = bufferManager.getSrcBuffer(ImtpsSession.SESSIONID_LENGTH);
            try {
                String token = SessionUtil.creationSessionId();
                byteBuffer.put(token.getBytes(StandardCharsets.UTF_8)).flip();
                while (byteBuffer.hasRemaining()) {
                    acceptChannel.getSocketChannel().write(byteBuffer);
                }
                verifyMap.put(token, acceptChannel.setString(token));
            } catch (Exception e) {
                closeSelectionKey(selectionKey);
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionHandshaker[ThreadPool] AriseError", e);
            } finally {
                selectionKey.cancel();
            }
        });
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    private void closeSelectionKey(SelectionKey selectionKey) {
        try {
            selectionKey.channel().close();
            selectionKey.cancel();
        } catch (Exception ignored) {}
    }
}
