package com.thezeroer.imtps.server.process.task;

import com.thezeroer.imtps.server.datapacket.DataPacket;

import java.util.Collection;
import java.util.List;

/**
 * IMTPS 任务
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/08/01
 */
public abstract class ImtpsTask extends AbstractTask {
    private DataPacket requestDataPacket, responseDataPacket;

    @Override
    public void putRequestDataPacket(DataPacket dataPacket) {
        requestDataPacket = dataPacket;
    }
    @Override
    public DataPacket getResponseDataPacket() {
        return responseDataPacket;
    }
    @Override
    public Collection<DataPacket> getResponseDataPackets() {
        return responseDataPacket == null ? List.of() : List.of(responseDataPacket);
    }

    @Override
    public List<DataPacket> getRequestDataPackets() {
        return requestDataPacket == null ? List.of() : List.of(requestDataPacket);
    }
    @Override
    public boolean putResponseDataPacket(DataPacket dataPacket) {
        responseDataPacket = dataPacket;
        return true;
    }
}
