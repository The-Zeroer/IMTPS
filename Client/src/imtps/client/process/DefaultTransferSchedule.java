package imtps.client.process;

/**
 * 默认传输计划
 *
 * @author NiZhanBo
 * @since 2025/03/14
 * @version 1.0.0
 */
public class DefaultTransferSchedule extends AbstractTransferSchedule {
    protected static int total = 100;
    protected String message, progressBar, speed;
    protected long sumSize, presentSize, lastUpdateTime, lastUpdateSize;
    protected int percent;

    public void setMessage(String message) {
        this.message = message;
    }
    public void setSumSize(long sumSize) {
        this.sumSize = sumSize;
        lastUpdateTime = System.currentTimeMillis();
        lastUpdateSize = 0;
        presentSize = 0;
        speed = "0B/S";
    }
    public void updateProgress(long progress) {
        lastUpdateSize += progress;
        presentSize += progress;
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - lastUpdateTime;
        if (diff > 100) {
            percent = (int) (total * presentSize / sumSize);
            progressBar = "=".repeat(percent) + " ".repeat(total - percent);
            speed = formatBytes((long) (lastUpdateSize / (diff / 1000.0))) + "/S";
            System.out.printf("\r%s[%s] %d%% %s", message, progressBar, percent, speed);
            lastUpdateTime = currentTime;
            lastUpdateSize = 0;
        }
    }
    public void transferFinish(String message) {
        System.out.printf("\r%s[%s] 100%% %s\n", message, "=".repeat(total), formatBytes(sumSize));
    }
}
