package com.thezeroer.imtps.server.process.task;

import com.thezeroer.imtps.server.datapacket.DataPacket;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * IMTPS 任务集合
 *
 * @author NiZhanBo
 * @since 2025/09/24
 * @version 1.0.0
 */
public abstract class ImtpsTaskSet extends AbstractTask {
    private final Set<DataPacket> requestDataPacket = new HashSet<>(8), responseDataPacket = new HashSet<>(8);
    private int packetCount;

    @Override
    public void putRequestDataPacket(DataPacket dataPacket) {
        if (packetCount >= Byte.MAX_VALUE) {
            throw new ArithmeticException("PacketCount too large(max=%d)".formatted(Byte.MAX_VALUE));
        }
        if (requestDataPacket.add(dataPacket)) {
            packetCount++;
        }
    }
    @Override
    public DataPacket getResponseDataPacket() {
        if (responseDataPacket.isEmpty()) {
            return null;
        } else {
            return responseDataPacket.iterator().next();
        }
    }
    @Override
    public Collection<DataPacket> getResponseDataPackets() {
        return responseDataPacket;
    }

    @Override
    public Collection<DataPacket> getRequestDataPackets() {
        return requestDataPacket;
    }
    @Override
    public boolean putResponseDataPacket(DataPacket dataPacket) {
        responseDataPacket.add(dataPacket);
        return responseDataPacket.size() >= packetCount;
    }
}
