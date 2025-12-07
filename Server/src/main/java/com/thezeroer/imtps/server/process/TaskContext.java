package com.thezeroer.imtps.server.process;

import com.thezeroer.imtps.server.datapacket.DataPacket;

import java.util.Collection;

/**
 * 任务上下文
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/12/03
 */
public abstract class TaskContext {
    public abstract void putRequestDataPacket(DataPacket dataPacket);
    public abstract DataPacket getResponseDataPacket();
    public abstract Collection<DataPacket> getResponseDataPackets();
}
