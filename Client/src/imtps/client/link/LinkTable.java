package imtps.client.link;

import imtps.client.datapacket.DataPacket;

import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 链接表
 *
 * @author NiZhanBo
 * @since 2025/02/27
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
    private final ConcurrentLinkedQueue<DataPacket> cacheDataPacketQueue;
    private SelectionKey baseSelectionKey, fileSelectionKey;
    private String token;
    private byte fileLinkState;

    public LinkTable() {
        cacheDataPacketQueue = new ConcurrentLinkedQueue<>();
    }
    public void setBaseSelectionKey(SelectionKey baseSelectionKey) {
        this.baseSelectionKey = baseSelectionKey;
    }
    public void setFileSelectionKey(SelectionKey fileSelectionKey) {
        this.fileSelectionKey = fileSelectionKey;
        if (baseSelectionKey != null) {
            fileLinkState = LINKSTATE_Linked;
        } else {
            fileLinkState = LINKSTATE_CLOSED;
        }
    }
    public SelectionKey getBaseSelectionKey() {
        return baseSelectionKey;
    }
    public SelectionKey getFileSelectionKey() {
        return fileSelectionKey;
    }
    public void removeSelectionKey(SelectionKey selectionKey) {
        if (baseSelectionKey == selectionKey) {
            baseSelectionKey = null;
        }else if (fileSelectionKey == selectionKey) {
            fileSelectionKey = null;
        }
    }

    public void setToken(String token) {
        this.token = token;
    }
    public String getToken() {
        return token;
    }

    public void setFileLinkState(byte state) {
        fileLinkState = state;
    }
    public byte getFileLinkState() {
        return fileLinkState;
    }

    public ConcurrentLinkedQueue<DataPacket> getCacheDataPacketQueue() {
        return cacheDataPacketQueue;
    }
}