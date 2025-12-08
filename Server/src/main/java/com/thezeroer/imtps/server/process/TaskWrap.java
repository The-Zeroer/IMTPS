package com.thezeroer.imtps.server.process;

import com.thezeroer.imtps.server.datapacket.DataPacket;
import com.thezeroer.imtps.server.process.task.*;
import com.thezeroer.imtps.server.session.ImtpsSession;
import com.thezeroer.imtps.server.view.AbstractTransmitView;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 任务包装
 *
 * @author NiZhanBo
 * @since 2025/12/07
 * @version 1.0.0
 */
public final class TaskWrap {
    private final AbstractTask<?> abstractTask;
    private final RequestFunction requestFunction;
    private final ResponseFunction responseFunction;
    private int requestCount, responseCount;
    private long expirationTime;
    private boolean done;
    private Map<Byte, AbstractTransmitView> sendViewMap, receiveViewMap;
    private ImtpsSession imtpsSession;

    public TaskWrap(AbstractTask<?> abstractTask) {
        this.abstractTask = abstractTask;
        switch (abstractTask) {
            case ImtpsTask task -> {
                requestFunction = () -> {
                    if (task.request() instanceof DataPacket dataPacket) {
                        return List.of(dataPacket);
                    } else {
                        return null;
                    }
                };
                responseFunction = (dataPacket) -> {
                    responseCount++;
                    task.response(dataPacket);
                    return true;
                };
            }
            case ImtpsTaskList taskList -> {
                AtomicReference<List<DataPacket>> responseList = new AtomicReference<>();
                requestFunction = () -> {
                    List<DataPacket> packets = taskList.request();
                    for (int i = 0; i < packets.size(); i++) {
                        packets.get(i).setPacketIndex((byte) i);
                    }
                    return packets;
                };
                responseFunction = (dataPacket) -> {
                    if (responseList.get() == null) {
                        responseList.set(new ArrayList<>(requestCount));
                    }
                    if (dataPacket == null) {
                         taskList.response(responseList.get());
                         return true;
                    }
                    responseList.get().add(dataPacket.getPacketIndex(), dataPacket);
                    responseCount++;
                    if (responseCount >= requestCount) {
                        taskList.response(responseList.get());
                        return true;
                    } else {
                        return false;
                    }
                };
            }
            case ImtpsTaskMap taskMap -> {
                Map<Byte, DataPacket> responseMap = new LinkedHashMap<>(8);
                requestFunction = () -> {
                    if (taskMap.request() instanceof Map<Byte, DataPacket> dataPacketMap) {
                        return new ArrayList<>(dataPacketMap.values());
                    } else {
                        return null;
                    }
                };
                responseFunction = (dataPacket) -> {
                    if (dataPacket == null) {
                        taskMap.response(responseMap);
                        return true;
                    }
                    if (responseMap.put(dataPacket.getPacketIndex(), dataPacket) == null) {
                        responseCount++;
                    }
                    if (responseCount >= requestCount) {
                        taskMap.response(responseMap);
                        return true;
                    } else {
                        return false;
                    }
                };
            }
            case ImtpsTaskSet taskSet -> {
                Set<DataPacket> responseSet = new LinkedHashSet<>(8);
                requestFunction = () -> {
                    if (taskSet.request() instanceof Set<DataPacket> dataPacketSet) {
                        return new ArrayList<>(dataPacketSet);
                    } else {
                        return null;
                    }
                };
                responseFunction = (dataPacket) -> {
                    if (dataPacket == null) {
                        taskSet.response(responseSet);
                        return true;
                    }
                    if (responseSet.add(dataPacket)) {
                        responseCount++;
                    }
                    if (responseCount >= requestCount) {
                        taskSet.response(responseSet);
                        return true;
                    } else {
                        return false;
                    }
                };
            }
            case null, default -> {
                throw new IllegalArgumentException("Unsupported task type: " + abstractTask);
            }
        }
    }

    public TaskWrap setSendTransmitView(byte packetIndex, AbstractTransmitView transmitView) {
        if (sendViewMap == null) {
            sendViewMap = new LinkedHashMap<>();
        }
        sendViewMap.put(packetIndex, transmitView);
        return this;
    }
    public TaskWrap setReceiveTransmitView(byte packetIndex, AbstractTransmitView transmitView) {
        if (receiveViewMap == null) {
            receiveViewMap = new LinkedHashMap<>();
        }
        receiveViewMap.put(packetIndex, transmitView);
        return this;
    }

    public AbstractTask<?> getTask() {
        return abstractTask;
    }
    public AbstractTransmitView getSendTransmitView(byte packetIndex) {
        return sendViewMap.get(packetIndex);
    }
    public AbstractTransmitView getReceiveTransmitView(byte packetIndex) {
        return receiveViewMap.get(packetIndex);
    }
    public boolean hasTransmitView() {
        return sendViewMap != null || receiveViewMap != null;
    }

    public TaskWrap setImtpsSession(ImtpsSession imtpsSession) {
        this.imtpsSession = imtpsSession;
        return this;
    }
    public ImtpsSession getImtpsSession() {
        return imtpsSession;
    }

    List<DataPacket> taskRequest() throws Exception {
        List<DataPacket> packets = requestFunction.get();
        if (packets == null || packets.isEmpty()) {
            done = true;
            abstractTask.done(false);
        } else {
            requestCount = packets.size();
        }
        return packets;
    }
    synchronized boolean taskResponse(DataPacket dataPacket) throws Exception {
        if (done) {
            return false;
        }
        done = responseFunction.accept(dataPacket);
        if (done) {
            abstractTask.done(true);
            if (abstractTask.getPattern() == AbstractTask.PATTERN_DEFAULT) {
                this.notifyAll();
            }
        }
        return done;
    }
    long getExpirationTime() {
        return expirationTime;
    }
    void updateExpirationTime() {
        expirationTime = System.currentTimeMillis() + abstractTask.getWaitingTime();
    }

    @FunctionalInterface
    private interface RequestFunction {
        List<DataPacket> get() throws Exception;
    }
    @FunctionalInterface
    private interface ResponseFunction {
        boolean accept(DataPacket dataPacket) throws Exception;
    }
}
