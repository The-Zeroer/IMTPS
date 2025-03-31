package imtps.client.process;

/**
 * 传输时间表，可视化数据体传输进度
 *
 * @author NiZhanBo
 * @since 2025/03/14
 * @version 1.0.0
 */
public abstract class AbstractTransferSchedule {
    /**
     * 设置消息
     *
     * @param message 消息
     */
    public abstract void setMessage(String message);
    /**
     * 设置总大小
     *
     * @param sumSize 总共需要传输的字节数
     */
    public abstract void setSumSize(long sumSize);
    /**
     * 更新进度
     *
     * @param progress 本次传输的字节数
     */
    public abstract void updateProgress(long progress);
    /**
     * 传输结束
     *
     * @param message 消息
     */
    public abstract void transferFinish(String message);

    protected static String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "0B";
        } else {
            String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
            int idx = (int) (Math.log(bytes) / Math.log(1024));
            if (idx < units.length) {
                return String.format("%.2f%s", bytes / Math.pow(1024, idx), units[idx]);
            } else {
                return "ERROR";
            }
        }
    }
}
