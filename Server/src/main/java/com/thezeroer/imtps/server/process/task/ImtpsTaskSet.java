package com.thezeroer.imtps.server.process.task;

import com.thezeroer.imtps.server.datapacket.DataPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * IMTPS 任务组
 *
 * @author NiZhanBo
 * @since 2025/09/24
 * @version 1.0.0
 */
public abstract class ImtpsTaskSet extends AbstractTask<List<DataPacket>> {
    private int dataPacketCount;

    public ImtpsTaskSet() {
        this(null);
    }
    public ImtpsTaskSet(String taskId) {
        super(taskId);
        response = new ArrayList<>();
    }

    @Override
    public boolean putResponseData(DataPacket dataPacket) {
        synchronized (this) {
            response.add(dataPacket);
            return response.size() >= dataPacketCount;
        }
    }

    public void setDataPacketCount(int dataPacketCount) {
        this.dataPacketCount = dataPacketCount;
    }
}
