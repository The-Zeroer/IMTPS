package imtp.server.log;

import java.text.SimpleDateFormat;

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
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("[").append(dateFormat.format(time)).append("] ");
        switch (level) {
            case ImtpLogger.LEVEL_TRACE -> messageBuilder.append("[TRACE] ");
            case ImtpLogger.LEVEL_DEBUG -> messageBuilder.append("[DEBUG] ");
            case ImtpLogger.LEVEL_INFO -> messageBuilder.append("[INFO] ");
            case ImtpLogger.LEVEL_WARN -> messageBuilder.append("[WARN] ");
            case ImtpLogger.LEVEL_ERROR -> messageBuilder.append("[ERROR] ");
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
                message = message.replaceFirst("\\$", arg == null ? "null" : arg.toString());
            }
        }
        messageBuilder.append("{ ").append(message).append(" }");
        return messageBuilder.toString();
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