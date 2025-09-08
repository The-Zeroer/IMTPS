package com.thezeroer.imtps.server.address;

import com.thezeroer.imtps.server.session.ImtpsSession;
import com.thezeroer.imtps.server.session.channel.ImtpsChannel;

import java.util.EnumMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地址管理器
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/29
 */
public class AddressManager {
    private String proxyHost;
    private final EnumMap<ImtpsChannel.TYPE, Integer> localPort;
    private final EnumMap<ImtpsChannel.TYPE, Integer> proxyPort;
    private final Set<Subnet> proxySubnetSet;

    public AddressManager() {
        localPort = new EnumMap<>(ImtpsChannel.TYPE.class);
        proxyPort = new EnumMap<>(ImtpsChannel.TYPE.class);
        proxySubnetSet = ConcurrentHashMap.newKeySet();
    }

    public String choose(ImtpsSession imtpsSession, ImtpsChannel.TYPE type) {
        if (proxySubnetSet.stream().anyMatch(subnet -> subnet.contains(imtpsSession.getRemoteAddress()))) {
            return proxyHost + ":" + proxyPort.get(type);
        } else {
            return imtpsSession.getLocalAddress().getHostAddress() + ":" + localPort.get(type);
        }
    }

    public AddressManager addProxySubnet(String cidr) {
        proxySubnetSet.add(new Subnet(cidr));
        return this;
    }
    public AddressManager setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
        return this;
    }

    public AddressManager setLocalPort(ImtpsChannel.TYPE type, int port) {
        localPort.put(type, port);
        return this;
    }
    public AddressManager setProxyPort(ImtpsChannel.TYPE type, int port) {
        proxyPort.put(type, port);
        return this;
    }
}
