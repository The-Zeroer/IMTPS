package com.thezeroer.imtps.client.view;

import com.thezeroer.imtps.client.datapacket.DataPacket;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;

/**
 * 默认传输视图
 *
 * @author NiZhanBo
 * @since 2025/08/19
 * @version 1.0.0
 */
public class DefaultTransmitView extends AbstractTransmitView {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private long lastSize, lastTime, beginTime;
    private String metadataString;

    @Override
    public void begin() {
        beginTime = System.currentTimeMillis();
        if (metadata == null) {
            metadataString = "";
        } else {
            metadataString = new String(metadata, StandardCharsets.UTF_8);
        }
        System.out.printf("[%s]正在传输数据体[%s], 大小[%s]。\n"
                , dateFormat.format(beginTime), metadataString
                , DataPacket.formatBytes(sumSize));
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                printProgress();
                Thread.sleep(1000);
            }
        } catch (InterruptedException ignored) {}
    }

    @Override
    public void finish() {
        Thread.currentThread().interrupt();
        System.out.printf("\r[%s]数据体传输完成[%s], 用时[%s]。\n"
                , dateFormat.format(System.currentTimeMillis()), metadataString
                , formatTime((System.currentTimeMillis() - beginTime) / 1000L));
    }

    private void printProgress() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTime;
        long diffSize = currentSize - lastSize;

        // 计算速度 (字节/秒)
        double speedBps = (elapsed > 0) ? (diffSize * 1000.0 / elapsed) : 0.0;
        String speedText = DataPacket.formatBytes((long) speedBps) + "/s";

        // 计算预计剩余时间
        long remainSize = sumSize - currentSize;
        String eta = (speedBps > 0)
                ? formatTime((long) (remainSize / speedBps))
                : "--:--";

        // 更新采样点
        lastSize = currentSize;
        lastTime = now;

        // 百分比 + 进度条
        int barWidth = 40;
        double percent = (sumSize == 0) ? 0.0 : (currentSize * 1.0 / sumSize);
        int filled = (int) (barWidth * percent);

        String progressBar = "=".repeat(filled) + " ".repeat(barWidth - filled);
        String text = String.format(
                "\r[%s] %.2f%% (%s/%s) | %s | 预计剩余时间 %s",
                progressBar,
                percent * 100,
                DataPacket.formatBytes(currentSize),
                DataPacket.formatBytes(sumSize),
                speedText,
                eta
        );
        System.out.print(text);
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%02d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }
}
