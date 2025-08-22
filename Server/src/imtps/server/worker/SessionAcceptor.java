package imtps.server.worker;

import imtps.server.log.ImtpsLogger;
import imtps.server.session.channel.AcceptChannel;
import imtps.server.session.channel.ImtpsChannel;

import java.io.IOException;
import java.nio.channels.*;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话接收器
 *
 * @author NiZhanBo
 * @since 2025/06/30
 * @version 1.0.0
 */
public class SessionAcceptor extends Thread{
    private final Selector selector;
    private final ConcurrentLinkedQueue<Runnable> eventQueue;
    private final EnumMap<ImtpsChannel.TYPE, ServerSocketChannel> channelMapping;
    private boolean live, running;
    private final Object lock = new Object();

    private final SessionFilter sessionFilter;
    private final ImtpsLogger imtpsLogger;

    private AtomicInteger atomicInteger = new AtomicInteger(0);

    public SessionAcceptor(SessionFilter sessionFilter, ImtpsLogger imtpsLogger) throws IOException {
        selector = Selector.open();
        eventQueue = new ConcurrentLinkedQueue<>();
        channelMapping = new EnumMap<>(ImtpsChannel.TYPE.class);

        this.sessionFilter = sessionFilter;
        this.imtpsLogger = imtpsLogger;
        live = true;
        setName("SessionAcceptor");
    }

    public void registerServerChannel(ServerSocketChannel serverSocketChannel, ImtpsChannel.TYPE channelType) throws IOException {
        eventQueue.add(() -> {
            try {
                if (channelMapping.remove(channelType) instanceof ServerSocketChannel oldServerSocketChannel) {
                    oldServerSocketChannel.close();
                    imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionAcceptor注销监听[$]通道成功", channelType.name());
                }
                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT).attach(channelType);
                imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionAcceptor注册监听[$]通道成功", channelType.name());
            } catch (ClosedChannelException e) {
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionAcceptor注册监听[$]通道出错", channelType.name(), e);
            } catch (IOException e) {
                imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionAcceptor注销监听[$]通道出错", channelType.name(), e);
            };
        });
        if (running) {
            selector.wakeup();
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
        }
    }
    public void stopRunning() {
        running = false;
        selector.wakeup();
        System.out.println("Acceptor stopped: " + atomicInteger.get());
    }
    public void shutdown() {
        live = false;
        running = false;
        selector.wakeup();
    }

    @Override
    public void run() {
        while (live) {
            imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionAcceptor StartRunning");
            while (running) {
                while (!eventQueue.isEmpty()) {
                    Runnable task = eventQueue.poll();
                    task.run();
                }
                try {
                    while (selector.select() > 0) {
                        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                        while (keys.hasNext() && running) {
                            SelectionKey key = keys.next(); keys.remove();
                            if (key.isAcceptable()) {
                                sessionFilter.transmit(new AcceptChannel(((ServerSocketChannel) key.channel()).accept()
                                        , (AcceptChannel.TYPE) key.attachment()));
                                atomicInteger.incrementAndGet();
                            }
                        }
                    }
                } catch (IOException e) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_ERROR, "SessionAcceptor AriseError", e);
                } catch (InterruptedException ignored) {}
            }
            if (live) {
                synchronized (lock) {
                    imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionAcceptor StopRunning");
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {}
                }
            }
        }
        imtpsLogger.log(ImtpsLogger.LEVEL_DEBUG, "SessionAcceptor Shutdown");
    }
}
