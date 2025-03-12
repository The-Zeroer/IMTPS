package imtp.server.util;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 过滤器-验证目标IP是否允许连接
 *
 * @author NiZhanBo
 * @since 2025/01/22
 * @version 1.0.0
 */
public class NetFilter {
    /** 仅允许白名单连接 */
    public static final byte LEACH_WHITE = 1;
    /** 不允许黑名单连接 */
    public static final byte LEACH_BLACK = -1;
    /** 关闭过滤器 */
    public static final byte LEACH_CLOSE = 0;

    /** 过滤模式 */
    private byte pattern;
    /** BigInteger[0]为IP地址,BigInteger[1]为子网掩码 */
    private final Set<BigInteger[]> whithListSet;
    private final Set<BigInteger[]> blackListSet;

    public NetFilter() {
        whithListSet = ConcurrentHashMap.newKeySet();
        blackListSet = ConcurrentHashMap.newKeySet();
    }

    /**
     * 设置当前工作的过滤模式
     *
     * @param pattern 过滤模式
     */
    public void setPattern(byte pattern) {
        this.pattern = pattern;
    }

    /**
     * 添加过滤项
     *
     * @param ip IP地址
     * @param mask 子网掩码
     * @param pattern 过滤模式
     * @throws UnknownHostException 未知主机异常
     */
    public void addListItem(String ip, String mask, byte pattern) throws UnknownHostException {
        switch (pattern) {
            case LEACH_WHITE -> whithListSet.add(new BigInteger[]{
                    ipToBigInt(InetAddress.getByName(ip)), ipToBigInt(InetAddress.getByName(mask))
            });
            case LEACH_BLACK -> blackListSet.add(new BigInteger[]{
                    ipToBigInt(InetAddress.getByName(ip)), ipToBigInt(InetAddress.getByName(mask))
            });
        }

    }

    /**
     * 移除过滤项
     *
     * @param ip IP地址
     * @param mask 子网掩码
     * @param pattern 过滤模式
     */
    public void removeListItem(String ip, String mask, byte pattern) throws UnknownHostException {
        switch (pattern) {
            case LEACH_WHITE -> whithListSet.remove(new BigInteger[]{
                    ipToBigInt(InetAddress.getByName(ip)), ipToBigInt(InetAddress.getByName(mask))
            });
            case LEACH_BLACK -> blackListSet.remove(new BigInteger[]{
                    ipToBigInt(InetAddress.getByName(ip)), ipToBigInt(InetAddress.getByName(mask))
            });
        }

    }

    /**
     * 验证目标IP是否允许连接
     *
     * @param socketAddress 套接字地址
     */
    public boolean verify(SocketAddress socketAddress) throws UnknownHostException {
        if (pattern == LEACH_CLOSE) {
            return true;
        }
        String ipString = socketAddress.toString().split("[/:]")[1];
        BigInteger ipBigInt = ipToBigInt(InetAddress.getByName(ipString));
        switch (pattern) {
            case LEACH_WHITE -> {
                for (BigInteger[] item : whithListSet) {
                    if (ipBigInt.and(item[1]).equals(item[0])) {
                        return true;
                    }
                }
                return false;
            }
            case LEACH_BLACK -> {
                for (BigInteger[] item : blackListSet) {
                    if (ipBigInt.and(item[1]).equals(item[0])) {
                        return false;
                    }
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static BigInteger ipToBigInt(InetAddress ip) {
        byte[] address = ip.getAddress();
        BigInteger bigInt = BigInteger.ZERO;
        for (byte b : address) {
            bigInt = bigInt.shiftLeft(8).add(BigInteger.valueOf(b & 0xFF));
        }
        return bigInt;
    }
}