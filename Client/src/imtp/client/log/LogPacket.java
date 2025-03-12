package imtp.client.log;

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
        StringBuilder builder = new StringBuilder();
        builder.append("[").append(dateFormat.format(time)).append("] ");
        switch (level) {
            case ImtpLogger.LEVEL_TRACE -> builder.append("[TRACE] ");
            case ImtpLogger.LEVEL_DEBUG -> builder.append("[DEBUG] ");
            case ImtpLogger.LEVEL_INFO -> builder.append("[INFO] ");
            case ImtpLogger.LEVEL_WARN -> builder.append("[WARN] ");
            case ImtpLogger.LEVEL_ERROR -> builder.append("[ERROR] ");
            default -> builder.append("[default] ");
        }
        for (Object arg : args) {
            if (arg instanceof Exception exception) {
                String className = exception.getClass().getName();
                String exceptionMessage = exception.getMessage();
                StackTraceElement[] stackTrace = exception.getStackTrace();
                builder.append("\n").append(className).append(": ").append(exceptionMessage);
                for (StackTraceElement stackTraceElement : stackTrace) {
                    builder.append("\n").append(stackTraceElement.toString());
                }
            } else {
                message = message.replaceFirst("\\$", arg == null ? "null" : arg.toString());
            }
        }
        builder.append("{").append(message).append("}");
        return builder.toString();
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