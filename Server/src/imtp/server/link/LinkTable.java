package imtp.server.link;

import imtp.server.datapacket.DataPacket;
import imtp.server.log.ImtpLogger;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 链接表
 *
 * @author NiZhanBo
 * @since 2025/02/26
 * @version 1.0.0
 */
public class LinkTable {
    /** 未连接 */
    public static final byte LINKSTATE_CLOSED = 0;
    /** 连接中 */
    public static final byte LINKSTATE_Linking = 1;
    /** 已连接 */
    public static final byte LINKSTATE_Linked = 2;
    /** 已就绪 */
    public static final byte LINKSTATE_READY = 3;
    private final ConcurrentHashMap<String, KeySet> uidToKeyHashMap;
    private final ConcurrentHashMap<SelectionKey, UIDSet> keyToUIDHashMap;
    private final ConcurrentHashMap<String, Byte> fileLinkStateHashMap;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<DataPacket>> cacheDataPacketHashMap;
    private ConcurrentHashMap<String, String> tokenVerifyHashMap;

    private final ImtpLogger imtpLogger;

    public LinkTable(ImtpLogger imtpLogger) {
        uidToKeyHashMap = new ConcurrentHashMap<>();
        keyToUIDHashMap = new ConcurrentHashMap<>();
        fileLinkStateHashMap = new ConcurrentHashMap<>();
        cacheDataPacketHashMap = new ConcurrentHashMap<>();
        tokenVerifyHashMap = new ConcurrentHashMap<>();
        this.imtpLogger = imtpLogger;
    }

    public boolean register(SelectionKey baseSelectionKey, String UID, String token) {
        if (uidToKeyHashMap.containsKey(UID) || keyToUIDHashMap.containsKey(baseSelectionKey)) {
            return false;
        } else {
            KeySet keySet = new KeySet();
            keySet.baseSelectionKey = baseSelectionKey;
            UIDSet uidSet = new UIDSet();
            uidSet.UID = UID;
            uidSet.token = token;
            uidToKeyHashMap.put(UID, keySet);
            uidToKeyHashMap.put(token, keySet);
            keyToUIDHashMap.put(baseSelectionKey, uidSet);
            fileLinkStateHashMap.put(UID, LINKSTATE_CLOSED);
            String remoteAddress = "null";
            try {
                remoteAddress = ((SocketChannel)baseSelectionKey.channel()).getRemoteAddress().toString();
            } catch (IOException ignored) {}
            imtpLogger.log(ImtpLogger.LEVEL_INFO, "UID [$] 已注册，并绑定连接 [$] ", UID, remoteAddress);
            return true;
        }
    }
    public void cancel(String UID) {
        KeySet keySet = uidToKeyHashMap.remove(UID);
        if (keySet != null) {
            if (keySet.baseSelectionKey != null) {
                UIDSet uidSet = keyToUIDHashMap.remove(keySet.baseSelectionKey);
                if (uidSet != null && uidSet.token != null) {
                    uidToKeyHashMap.remove(uidSet.token);
                }
            }
            if (keySet.fileSelectionKey != null) {
                keyToUIDHashMap.remove(keySet.fileSelectionKey);
            }
            imtpLogger.log(ImtpLogger.LEVEL_INFO, "UID [$] 已注销", UID);
        }
        fileLinkStateHashMap.remove(UID);
    }
    public void cancel(SelectionKey baseSelectionKey) {
        UIDSet uidSet = keyToUIDHashMap.remove(baseSelectionKey);
        if (uidSet != null) {
            if (uidSet.UID != null) {
                KeySet keySet = uidToKeyHashMap.remove(uidSet.UID);
                if (keySet != null && keySet.fileSelectionKey != null) {
                    keyToUIDHashMap.remove(keySet.fileSelectionKey);
                }
                fileLinkStateHashMap.remove(uidSet.UID);
                imtpLogger.log(ImtpLogger.LEVEL_INFO, "UID [$] 已注销", uidSet.UID);
            }
            if (uidSet.token != null) {
                uidToKeyHashMap.remove(uidSet.token);
            }
        }
    }
    public void updateBaseLink(SelectionKey oldSelectionKey, SelectionKey newSelectionKey) {

    }

    public String bindFileSelectionKey(SelectionKey baseSelectionKey, SelectionKey fileSelectionKey) {
        UIDSet uidSet = keyToUIDHashMap.get(baseSelectionKey);
        if (uidSet != null && uidSet.UID != null) {
            keyToUIDHashMap.put(fileSelectionKey, uidSet);
            fileLinkStateHashMap.put(uidSet.UID, LINKSTATE_Linked);
            KeySet keySet = uidToKeyHashMap.get(uidSet.UID);
            if (keySet != null) {
                keySet.fileSelectionKey = fileSelectionKey;
            }
            return uidSet.UID;
        } else {
            return "";
        }
    }
    public void unbindFileSelectionKey(SelectionKey fileSelectionKey) {
        UIDSet uidSet = keyToUIDHashMap.remove(fileSelectionKey);
        if (uidSet != null && uidSet.UID != null) {
            KeySet keySet = uidToKeyHashMap.get(uidSet.UID);
            fileLinkStateHashMap.put(uidSet.UID, LINKSTATE_CLOSED);
            if (keySet != null) {
                keySet.fileSelectionKey = null;
            }
        }
    }

    public String getUID(SelectionKey selectionKey) {
        UIDSet uidSet = keyToUIDHashMap.get(selectionKey);
        if (uidSet != null) {
            return uidSet.UID;
        } else {
            return null;
        }
    }
    public String getToken(SelectionKey selectionKey) {
        UIDSet uidSet = keyToUIDHashMap.get(selectionKey);
        if (uidSet != null) {
            return uidSet.token;
        } else {
            return null;
        }
    }

    public SelectionKey getBaseSelectionKey(String UIDOrToken) {
        KeySet keySet = uidToKeyHashMap.get(UIDOrToken);
        if (keySet != null) {
            return keySet.baseSelectionKey;
        } else {
            return null;
        }
    }
    public SelectionKey getFileSelectionKey(String UIDOrToken) {
        KeySet keySet = uidToKeyHashMap.get(UIDOrToken);
        if (keySet != null) {
            return keySet.fileSelectionKey;
        } else {
            return null;
        }
    }

    public void setFileLinkState(String UID, byte state) {
        fileLinkStateHashMap.put(UID, state);
    }
    public byte getFileLinkState(String UID) {
        return fileLinkStateHashMap.get(UID);
    }

    public Queue<DataPacket> getCacheDataPacketQueue(String UID) {
        return cacheDataPacketHashMap.computeIfAbsent(UID, k -> new ConcurrentLinkedQueue<>());
    }
    public void removeCacheDataPacketQueue(String UID) {
        cacheDataPacketHashMap.remove(UID);
    }

    public ConcurrentHashMap<String, String> getTokenVerifyHashMap() {
        ConcurrentHashMap<String, String> tokenVerifyHashMap = new ConcurrentHashMap<>();
        for (UIDSet uidSet : keyToUIDHashMap.values()) {
            tokenVerifyHashMap.putIfAbsent(uidSet.token, uidSet.UID);
        }
        return tokenVerifyHashMap;
    }
    public void setTokenVerifyHashMap(ConcurrentHashMap<String, String> tokenVerifyHashMap) {
        this.tokenVerifyHashMap = tokenVerifyHashMap;
    }
    public boolean addVerifyToken(String token, String UID) {
        return tokenVerifyHashMap.putIfAbsent(token, UID) != null;
    }
    public String verifyToken(String token) {
        return tokenVerifyHashMap.remove(token);
    }

    static class KeySet {
        public SelectionKey baseSelectionKey, fileSelectionKey;
    }
    static class UIDSet {
        public String UID, token;
    }
}