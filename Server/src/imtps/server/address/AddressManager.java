package imtps.server.address;

import imtps.server.session.ImtpsSession;
import imtps.server.session.channel.ImtpsChannel;

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
    private String proxyIp;
    private final EnumMap<ImtpsChannel.TYPE, Integer> hostPort;
    private final EnumMap<ImtpsChannel.TYPE, Integer> proxyPort;
    private final Set<Subnet> proxySubnetSet;

    public AddressManager() {
        hostPort = new EnumMap<>(ImtpsChannel.TYPE.class);
        proxyPort = new EnumMap<>(ImtpsChannel.TYPE.class);
        proxySubnetSet = ConcurrentHashMap.newKeySet();
    }

    public String choose(ImtpsSession imtpsSession, ImtpsChannel.TYPE type) {
        if (proxySubnetSet.stream().anyMatch(subnet -> subnet.contains(imtpsSession.getRemoteAddress()))) {
            return proxyIp + ":" + proxyPort.get(type);
        } else {
            return imtpsSession.getLocalAddress().getHostAddress() + ":" + hostPort.get(type);
        }
    }

    public void addProxySubnet(String cidr) {
        proxySubnetSet.add(new Subnet(cidr));
    }
    public void setProxyIp(String proxyIp) {
        this.proxyIp = proxyIp;
    }

    public void setHostPort(ImtpsChannel.TYPE type, int port) {
        hostPort.put(type, port);
    }
    public void setProxyPort(ImtpsChannel.TYPE type, int port) {
        proxyPort.put(type, port);
    }
}
