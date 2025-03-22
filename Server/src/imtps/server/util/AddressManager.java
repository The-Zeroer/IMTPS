package imtps.server.util;

import java.util.HashSet;
import java.util.Set;

/**
 * 地址管理器
 *
 * @author NiZhanBo
 * @since 2025/02/26
 * @version 1.0.0
 */
public class AddressManager {
    private final Set<String> frpHostSet;
    private String LAN_FileLinkAddress, LAN_WebServerAddress, WAN_FileLinkAddress, WAN_WebServerAddress;
    private int basePort, filePort, webPort;

    public AddressManager() {
        frpHostSet = new HashSet<>();
    }
    public void addFrpHost(String frpHost) {
        frpHostSet.add(frpHost);
    }
    public void removeFrpHost(String frpHost) {
        frpHostSet.remove(frpHost);
    }
    public void setLanFileLinkAddress(String lanFileLinkAddress) {
        LAN_FileLinkAddress = lanFileLinkAddress;
    }
    public void setLanWebServerAddress(String lanWebServerAddress) {
        LAN_WebServerAddress = lanWebServerAddress;
    }
    public void setWanFileLinkAddress(String wanFileLinkAddress) {
        WAN_FileLinkAddress = wanFileLinkAddress;
    }
    public void setWanWebServerAddress(String wanWebServerAddress) {
        WAN_WebServerAddress = wanWebServerAddress;
    }
    public String getFileLinkAddress(String ip) {
        if (frpHostSet.contains(ip)) {
            return WAN_FileLinkAddress;
        } else {
            return LAN_FileLinkAddress;
        }
    }
    public String getWebServerAddress(String ip) {
        if (frpHostSet.contains(ip)) {
            return WAN_WebServerAddress;
        } else {
            return LAN_WebServerAddress;
        }
    }

    public void setBasePort(int port) {
        basePort = port;
    }
    public void setFilePort(int port) {
        filePort = port;
    }
    public void setWebPort(int port) {
        webPort = port;
    }
    public int getBasePort() {
        return basePort;
    }
    public int getFilePort() {
        return filePort;
    }
    public int getWebPort() {
        return webPort;
    }
}