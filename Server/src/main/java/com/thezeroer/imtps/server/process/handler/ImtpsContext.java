package com.thezeroer.imtps.server.process.handler;

import com.thezeroer.imtps.server.datapacket.DataPacket;
import com.thezeroer.imtps.server.session.ImtpsSession;
import com.thezeroer.imtps.server.session.channel.ImtpsChannel;
import com.thezeroer.imtps.server.worker.SessionManager;

import java.net.InetAddress;

/**
 * IMTPS 上下文
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/31
 */
public class ImtpsContext {
    private final SessionManager sessionManager;
    private final ImtpsSession imtpsSession;
    private final DataPacket requestDataPacket;

    public ImtpsContext(SessionManager sessionManager, ImtpsSession imtpsSession, DataPacket requestDataPacket) {
        this.sessionManager = sessionManager;
        this.imtpsSession = imtpsSession;
        this.requestDataPacket = requestDataPacket;
    }

    /**
     * 获取请求数据包
     *
     * @return {@link DataPacket }
     */
    public DataPacket getRequestDataPacket() {
        return requestDataPacket;
    }
    /**
     * 放置响应数据包
     *
     * @param dataPacket 数据包
     */
    public void putResponseDataPacket(DataPacket dataPacket) {
        sessionManager.putDataPacket(imtpsSession, dataPacket.setTaskId(requestDataPacket.getTaskId()));
    }
    public void putDataPacket(DataPacket dataPacket) {
        sessionManager.putDataPacket(imtpsSession, dataPacket);
    }

    /**
     * 设置会话名称
     *
     * @param sessionName 会话名称
     * @param replace 会话名冲突时，是否替换旧会话
     * @return boolean 是否替换了旧会话
     */
    public boolean setSessionName(String sessionName, boolean replace) {
        imtpsSession.setSessionName(sessionName);
        if (replace) {
            ImtpsSession repetitionSession = sessionManager.getNameToSessionHashMap().put(sessionName, imtpsSession);
            if (repetitionSession != null) {
                sessionManager.closeChannel(repetitionSession, ImtpsChannel.TYPE.Control, "会话名冲突，关闭旧会话");
                return true;
            } else {
                return false;
            }
        } else {
            sessionManager.getNameToSessionHashMap().putIfAbsent(sessionName, imtpsSession);
            return false;
        }
    }
    /**
     * 获取会话名称
     *
     * @return {@link String }
     */
    public String getSessionName() {
        return imtpsSession.getSessionName();
    }
    public void setAttachment(Object attachment) {
        imtpsSession.setAttachment(attachment);
    }
    public Object getAttachment() {
        return imtpsSession.getAttachment();
    }
    /**
     * 获取远程地址
     *
     * @return {@link InetAddress }
     */
    public InetAddress getRemoteAddress() {
        return imtpsSession.getRemoteAddress();
    }
}
