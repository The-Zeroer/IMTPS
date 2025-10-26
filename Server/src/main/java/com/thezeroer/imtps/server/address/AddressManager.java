package com.thezeroer.imtps.server.address;

import com.thezeroer.imtps.server.session.ImtpsSession;
import com.thezeroer.imtps.server.session.channel.ImtpsChannel;

import java.net.InetAddress;
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
    private String serverHostName;
    private final EnumMap<ImtpsChannel.TYPE, Integer> localPort;
    private final EnumMap<ImtpsChannel.TYPE, Integer> proxyPort;
    private final Set<Subnet> proxySubnetSet;

    public AddressManager() {
        localPort = new EnumMap<>(ImtpsChannel.TYPE.class);
        proxyPort = new EnumMap<>(ImtpsChannel.TYPE.class);
        proxySubnetSet = ConcurrentHashMap.newKeySet();
    }

    public int choose(InetAddress remoteAddress, ImtpsChannel.TYPE type) {
        if (proxySubnetSet.stream().anyMatch(subnet -> subnet.contains(remoteAddress))) {
            return proxyPort.get(type);
        } else {
            return localPort.get(type);
        }
    }

    public AddressManager addProxySubnet(String cidr) {
        proxySubnetSet.add(new Subnet(cidr));
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

    public AddressManager setServerHostName(String serverHostName) {
        this.serverHostName = serverHostName;
        return this;
    }
    public String getServerHostName() {
        return serverHostName;
    }
}
