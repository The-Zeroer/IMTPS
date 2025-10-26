package com.thezeroer.imtps.client.log;

import java.text.SimpleDateFormat;
import java.util.regex.Matcher;

/**
 * 日志包
 *
 * @author NiZhanBo
 * @since 2025/01/22
 * @version 1.0.0
 */
public class LogPacket {
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private long time;
    private byte level;
    private String message;
    private Object[] args;

    public LogPacket(byte level, String message, Object[] args) {
        this.time = System.currentTimeMillis();
        this.level = level;
        this.message = message;
        this.args = args;
    }

    public String formatLog() {
        try {
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append("[").append(dateFormat.format(time)).append("] ");
            switch (level) {
                case ImtpsLogger.LEVEL_TRACE -> messageBuilder.append("[TRACE] ");
                case ImtpsLogger.LEVEL_DEBUG -> messageBuilder.append("[DEBUG] ");
                case ImtpsLogger.LEVEL_INFO -> messageBuilder.append("[INFO] ");
                case ImtpsLogger.LEVEL_WARN -> messageBuilder.append("[WARN] ");
                case ImtpsLogger.LEVEL_ERROR -> messageBuilder.append("[ERROR] ");
                default -> messageBuilder.append("[default] ");
            }
            for (Object arg : args) {
                if (arg instanceof Exception exception) {
                    StringBuilder exceptionBuilder = new StringBuilder();
                    String className = exception.getClass().getName();
                    String exceptionMessage = exception.getMessage();
                    StackTraceElement[] stackTrace = exception.getStackTrace();
                    exceptionBuilder.append("\n").append(className).append(": ").append(exceptionMessage);
                    for (StackTraceElement stackTraceElement : stackTrace) {
                        exceptionBuilder.append("\n").append(stackTraceElement.toString());
                    }
                    message = String.format("%s%s", message, exceptionBuilder);
                } else {
                    message = message.replaceFirst("\\$", Matcher.quoteReplacement(arg == null ? "null" : arg.toString()));
                }
            }
            messageBuilder.append("{ ").append(message).append(" }");
            return messageBuilder.toString();
        } catch (Exception e) {
            StringBuilder exceptionBuilder = new StringBuilder();
            String className = e.getClass().getName();
            String exceptionMessage = e.getMessage();
            StackTraceElement[] stackTrace = e.getStackTrace();
            exceptionBuilder.append("\n").append(className).append(": ").append(exceptionMessage);
            for (StackTraceElement stackTraceElement : stackTrace) {
                exceptionBuilder.append("\n").append(stackTraceElement.toString());
            }
            return exceptionBuilder.toString();
        }
    }

    public void setDateFormat(SimpleDateFormat dateFormat) {
        if (dateFormat != null) {
            LogPacket.dateFormat = dateFormat;
        }
    }

    public long getTime() {
        return time;
    }
    public byte getLevel() {
        return level;
    }
    public String getMessage() {
        return message;
    }
    public Object[] getArgs() {
        return args;
    }
}