package com.thezeroer.imtps.client.process.task;

import com.thezeroer.imtps.client.datapacket.DataPacket;

/**
 * IMTPS 任务
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/08/01
 */
public abstract class ImtpsTask extends AbstractTask<DataPacket> {
    public ImtpsTask() {
        this(null);
    }
    public ImtpsTask(String taskId) {
        super(taskId);
    }

    @Override
    public boolean putResponseData(DataPacket dataPacket) {
        response = dataPacket;
        return true;
    }
}
