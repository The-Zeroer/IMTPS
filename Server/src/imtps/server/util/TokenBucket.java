package imtps.server.util;

/**
 * 令牌桶
 *
 * @author NiZhanBo
 * @since 2025/01/22
 * @version 1.0.0
 */
public class TokenBucket {
    private final long capacity; // 桶的容量
    private final long rate; // 令牌生成速率（每秒生成的令牌数量）
    private long tokens; // 当前令牌数
    private long lastTimestamp; // 上次更新时间

    /**
     * 构造令牌桶
     *
     * @param capacity 令牌桶的总容量
     * @param rate 每秒产生的令牌数
     */
    public TokenBucket(long capacity, long rate) {
        this.capacity = capacity;
        this.rate = rate;
        this.tokens = capacity;
        this.lastTimestamp = System.currentTimeMillis();
    }

    /**
     * 获取令牌
     */
    public synchronized boolean acquire() {
        if (tokens > 0) {
            tokens--;
            return true;
        } else {
            long now = System.currentTimeMillis();
            long elapsedTime = now - lastTimestamp;
            long generatedTokens = elapsedTime * rate / 1000;
            if (generatedTokens >= 1) {
                tokens = Math.min(capacity, generatedTokens);
                lastTimestamp = now;
                return true;
            } else {
                return false;
            }
        }
    }
}