package com.thezeroer.imtps.server.log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 日志记录器
 *
 * @author NiZhanBo
 * @since 2025/01/22
 * @version 1.0.0
 */
public class ImtpsLogger extends Thread {
    public static final byte LEVEL_TRACE = 0;
    public static final byte LEVEL_DEBUG = 1;
    public static final byte LEVEL_INFO = 2;
    public static final byte LEVEL_WARN = 3;
    public static final byte LEVEL_ERROR = 4;
    public static final byte LEVEL_OFF = 5;

    private final BlockingQueue<LogPacket> logQueue;
    private LogHandler logHandler;
    private byte level;

    public ImtpsLogger() {
        logQueue = new LinkedBlockingQueue<>();
        logHandler = logPacket -> {
            System.out.println(logPacket.formatLog());
        };
        setDaemon(true);
        start();
    }

    public void setLevel(byte level) {
        this.level = level;
    }
    public void setLogHandler(LogHandler logHandler) {
        this.logHandler = logHandler;
    }

    public void log(byte level, String message, Object... args) {
        if (level >= this.level) {
            logQueue.add(new LogPacket(level, message, args));
        }
    }
    public void trace(String message, Object... args) {
        log(LEVEL_TRACE, message, args);
    }

    @Override
    public void run() {
        while (true) {
            try {
                LogPacket logPacket = logQueue.take();
                logHandler.handle(logPacket);
            } catch (InterruptedException ignored) {}
        }
    }
}