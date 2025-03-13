package imtp.client.process;

public class TransferSchedule {
    protected static int total = 100;
    protected String message, progressBar, speed;
    protected long sumSize, presentSize = 0, lastUpdateTime, lastUpdateSize;
    protected int percent;

    public void setMessage(String message) {
        this.message = message;
    }
    public void setSumSize(long sumSize) {
        this.sumSize = sumSize;
        lastUpdateTime = System.currentTimeMillis();
        lastUpdateSize = 0;
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

    private static String formatBytes(long bytes) {
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
