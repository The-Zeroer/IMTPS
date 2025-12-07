package com.thezeroer.imtps.client.process;

import com.thezeroer.imtps.client.datapacket.DataPacket;
import com.thezeroer.imtps.client.process.task.AbstractTask;
import com.thezeroer.imtps.client.process.task.ImtpsTask;
import com.thezeroer.imtps.client.process.task.ImtpsTaskMap;
import com.thezeroer.imtps.client.process.task.ImtpsTaskSet;

import java.util.*;

/**
 * 任务包装
 *
 * @author NiZhanBo
 * @since 2025/12/07
 * @version 1.0.0
 */
public class TaskWrap {
    private final AbstractTask<?> abstractTask;
    private final RequestFunction requestFunction;
    private final ResponseFunction responseFunction;
    private int requestCount, responseCount;
    private long expirationTime;
    private boolean done;

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
            case null, default -> {
                throw new IllegalArgumentException("Unsupported task type: " + abstractTask);
            }
        }
    }

    public AbstractTask<?> getTask() {
        return abstractTask;
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
