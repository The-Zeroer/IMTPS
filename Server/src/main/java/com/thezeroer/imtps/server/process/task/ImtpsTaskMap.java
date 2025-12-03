package com.thezeroer.imtps.server.process.task;

import com.thezeroer.imtps.server.datapacket.DataPacket;

import java.util.*;

/**
 * IMTPS任务图
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/12/02
 */
public abstract class ImtpsTaskMap extends AbstractTask {
    private final Map<Byte, DataPacket> requestDataPacket = new HashMap<>(8), responseDataPacket = new HashMap<>(8);
    private int packetCount;

    public void putRequestDataPacket(byte packetIndex, DataPacket dataPacket) {
        if (packetCount >= Byte.MAX_VALUE) {
            throw new ArithmeticException("PacketCount too large(max=%d)".formatted(Byte.MAX_VALUE));
        }
        if (requestDataPacket.put(packetIndex, dataPacket) == null) {
            packetCount++;
        }
    }
    public DataPacket getResponseDataPacket(byte packetIndex) {
        return responseDataPacket.get(packetIndex);
    }
    @Override
    public void putRequestDataPacket(DataPacket dataPacket) {
        if (packetCount >= Byte.MAX_VALUE) {
            throw new ArithmeticException("PacketCount too large(max=%d)".formatted(Byte.MAX_VALUE));
        }
        requestDataPacket.put((byte) packetCount, dataPacket.setPacketIndex((byte) packetCount++));
    }
    @Override
    public DataPacket getResponseDataPacket() {
        if (responseDataPacket.isEmpty()) {
            return null;
        } else {
            return responseDataPacket.values().iterator().next();
        }
    }
    @Override
    public Collection<DataPacket> getResponseDataPackets() {
        return responseDataPacket.values();
    }

    @Override
    public Collection<DataPacket> getRequestDataPackets() {
        return requestDataPacket.values();
    }
    @Override
    public boolean putResponseDataPacket(DataPacket dataPacket) {
        responseDataPacket.put(dataPacket.getPacketIndex(), dataPacket);
        return responseDataPacket.size() >= packetCount;
    }
}
