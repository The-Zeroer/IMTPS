package com.thezeroer.imtps.server.worker.Inspecter;

import com.thezeroer.imtps.server.address.Subnet;
import com.thezeroer.imtps.server.log.ImtpsLogger;

import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态 IP 检查器
 *
 * @author NiZhanBo
 * @since 2025/08/13
 * @version 1.0.0
 */
public final class StaticIpInspecter extends Inspecter {
    public enum Pattern {
        /** 关闭过滤器 */
        CLOSE,
        /** 不允许黑名单连接 */
        BLACK,
        /** 仅允许白名单连接 */
        WHITE
    }
    /** 过滤模式 */
    private Pattern pattern;
    private final Set<Subnet> blacklist, whitelist;

    public StaticIpInspecter() {
        blacklist = ConcurrentHashMap.newKeySet();
        whitelist = ConcurrentHashMap.newKeySet();
        logSheet = new ConcurrentHashMap<>();
        pattern = Pattern.CLOSE;
    }

    @Override
    public boolean inspect(InetAddress inetAddress) {
        switch (pattern) {
            case CLOSE -> {
                return true;
            }
            case BLACK -> {
                if (blacklist.stream().noneMatch(subnet -> subnet.contains(inetAddress))) {
                    return true;
                } else {
                    inspectLogSheet(inetAddress);
                    return false;
                }
            }
            case WHITE -> {
                if (whitelist.stream().anyMatch(subnet -> subnet.contains(inetAddress))) {
                    return true;
                } else {
                    inspectLogSheet(inetAddress);
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    protected void log() {
        imtpsLogger.log(ImtpsLogger.LEVEL_INFO, "StaticIpInspecter[$]模式拦截记录$",pattern.name(), logSheet);
    }

    public StaticIpInspecter setPattern(Pattern pattern) {
        this.pattern = pattern;
        return this;
    }
    public StaticIpInspecter addItem(String cidr, Pattern pattern) {
        switch (pattern) {
            case BLACK -> blacklist.add(new Subnet(cidr));
            case WHITE -> whitelist.add(new Subnet(cidr));
        }
        return this;
    }
    public StaticIpInspecter removeItem(String cidr, Pattern pattern) {
        switch (pattern) {
            case BLACK -> blacklist.remove(new Subnet(cidr));
            case WHITE -> whitelist.remove(new Subnet(cidr));
        }
        return this;
    }
}
