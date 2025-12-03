package com.thezeroer.imtps.server.datapacket;

import com.thezeroer.imtps.server.buffer.BufferManager;
import com.thezeroer.imtps.server.datapacket.databody.AbstractDataBody;
import com.thezeroer.imtps.server.security.ImtpsSecretKey;
import com.thezeroer.imtps.server.view.AbstractTransmitView;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * 数据包编解码器
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/14
 */
public class PacketCodec {
    private static final int MAX_BUFFER_SIZE = BufferManager.BUFFER_SIZE;
    private final ConcurrentHashMap<Long, Supplier<? extends AbstractDataBody<?>>> bodyMap;
    private final ConcurrentHashMap<String, AbstractTransmitView> sendViewMap, receiveViewMap;

    private ExecutorService threadPool;

    public PacketCodec() {
        bodyMap = new ConcurrentHashMap<>();
        sendViewMap = new ConcurrentHashMap<>();
        receiveViewMap = new ConcurrentHashMap<>();
    }

    public void registerDataBody(Supplier<? extends AbstractDataBody<?>> constructor) {
        bodyMap.putIfAbsent(constructor.get().getId(), constructor);
    }
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public void addSendView(String taskId, AbstractTransmitView transmitView) {
        sendViewMap.put(taskId, transmitView);
    }
    public void removeSendView(String taskId) {
        sendViewMap.remove(taskId);
    }
    public void addReceiveView(String taskId, AbstractTransmitView transmitView) {
        receiveViewMap.put(taskId, transmitView);
    }
    public void removeReceiveView(String taskId) {
        receiveViewMap.remove(taskId);
    }

    public DataPacket readDataPacket(SelectionKey selectionKey, ImtpsSecretKey imtpsSecretKey) throws Exception {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        BufferManager bufferManager = BufferManager.get();
        ByteBuffer srcBuffer = bufferManager.getSrcBuffer(ImtpsSecretKey.NONCE_LENGTH + DataPacket.BASIC_HEADER_LENGTH);
        ByteBuffer dstBuffer = bufferManager.getDestBuffer(DataPacket.BASIC_HEADER_LENGTH);
        while (srcBuffer.hasRemaining()) {
            if (socketChannel.read(srcBuffer) == -1) {
                return null;
            }
        }
        byte[] nonce = new byte[ImtpsSecretKey.NONCE_LENGTH];
        srcBuffer.flip().get(nonce);
        ImtpsSecretKey.DecryptCipher decryptCipher = imtpsSecretKey.createDecryptCipher(nonce);
        decryptCipher.update(srcBuffer, dstBuffer);
        DataPacket dataPacket = DataPacket.basicHeaderFromBuffer(dstBuffer.flip());
        srcBuffer = bufferManager.getSrcBuffer(dataPacket.getVariableHeaderLength() + ImtpsSecretKey.TAG_LENGTH);
        dstBuffer = bufferManager.getDestBuffer(dataPacket.getVariableHeaderLength());
        while (srcBuffer.hasRemaining()) {
            if (socketChannel.read(srcBuffer) == -1) {
                return null;
            }
        }
        dataPacket.variableHeaderFormBuffer(decryptCipher.doFinal(srcBuffer.flip(), dstBuffer).flip());
        if (dataPacket.getBodyCount() > 0) {
            AbstractDataBody<?>[] bodys = new AbstractDataBody[dataPacket.getBodyCount()];
            if (sendViewMap.get(dataPacket.getTaskId()) instanceof AbstractTransmitView transmitView) {
                if (transmitView.trySubmit()) {
                    threadPool.submit(transmitView);
                }
                for (int i = 0; i < dataPacket.getBodyCount(); i++) {
                    if (bodyMap.get(dataPacket.getBodyId(i)) instanceof Supplier<? extends AbstractDataBody<?>> supplier) {
                        AbstractDataBody<?> body = supplier.get();
                        try {
                            int bodyMeatLength  = dataPacket.getBodyMeatLength(i);
                            if (bodyMeatLength > 0) {
                                srcBuffer = bufferManager.getSrcBuffer(bodyMeatLength);
                                while (srcBuffer.hasRemaining()) {
                                    if (socketChannel.read(srcBuffer) == -1) {
                                        return null;
                                    }
                                }
                                byte[] meat = new byte[bodyMeatLength];
                                srcBuffer.flip().get(meat);
                                body.setMetadata(decryptCipher.update(meat));
                            }
                            long bodySize = dataPacket.getBodySize(i);
                            transmitView.setInfo(dataPacket.getPacketIndex(), bodySize, body.getMetadata());
                            body.prepareDecode(bodySize);
                            transmitView.begin();
                            int bufferSize = (int) Math.min(bodySize, MAX_BUFFER_SIZE);
                            srcBuffer = bufferManager.getSrcBuffer(bufferSize);
                            dstBuffer = bufferManager.getDestBuffer(bufferSize);
                            for (long residue = bodySize, handleNumber; residue > 0; residue -= handleNumber) {
                                if (residue < srcBuffer.remaining()) {
                                    srcBuffer.limit((int) residue);
                                }
                                while (srcBuffer.hasRemaining()) {
                                    if (socketChannel.read(srcBuffer) == -1) {
                                        return null;
                                    }
                                }
                                handleNumber = decryptCipher.update(srcBuffer.flip(), dstBuffer.clear());
                                body.decode(dstBuffer.flip());
                                srcBuffer.clear();
                                transmitView.updateSize(handleNumber);
                            }
                            body.finishDecode();
                            bodys[i] = body;
                        } finally {
                            body.release();
                            transmitView.finish();
                        }
                    } else {
                        throw new RuntimeException("未知的AbstractDataBodyId: " + dataPacket.getBodyId(i));
                    }
                }
            } else {
                for (int i = 0; i < dataPacket.getBodyCount(); i++) {
                    if (bodyMap.get(dataPacket.getBodyId(i)) instanceof Supplier<? extends AbstractDataBody<?>> supplier) {
                        AbstractDataBody<?> body = supplier.get();
                        try {
                            int bodyMeatLength  = dataPacket.getBodyMeatLength(i);
                            if (bodyMeatLength > 0) {
                                srcBuffer = bufferManager.getSrcBuffer(bodyMeatLength);
                                while (srcBuffer.hasRemaining()) {
                                    if (socketChannel.read(srcBuffer) == -1) {
                                        return null;
                                    }
                                }
                                byte[] meat = new byte[bodyMeatLength];
                                srcBuffer.flip().get(meat);
                                body.setMetadata(decryptCipher.update(meat));
                            }
                            long bodySize = dataPacket.getBodySize(i);
                            body.prepareDecode(bodySize);
                            int bufferSize = (int) Math.min(bodySize, MAX_BUFFER_SIZE);
                            srcBuffer = bufferManager.getSrcBuffer(bufferSize);
                            dstBuffer = bufferManager.getDestBuffer(bufferSize);
                            for (long residue = bodySize, handleNumber; residue > 0; residue -= handleNumber) {
                                if (residue < srcBuffer.remaining()) {
                                    srcBuffer.limit((int) residue);
                                }
                                while (srcBuffer.hasRemaining()) {
                                    if (socketChannel.read(srcBuffer) == -1) {
                                        return null;
                                    }
                                }
                                handleNumber = decryptCipher.update(srcBuffer.flip(), dstBuffer.clear());
                                body.decode(dstBuffer.flip());
                                srcBuffer.clear();
                            }
                            body.finishDecode();
                            bodys[i] = body;
                        } finally {
                            body.release();
                        }
                    } else {
                        throw new RuntimeException("未知的AbstractDataBodyId: " + dataPacket.getBodyId(i));
                    }
                }
            }
            dataPacket.setBodys(bodys);
        }
        if (dataPacket.getTailLength() > 0) {
            srcBuffer = bufferManager.getSrcBuffer(dataPacket.getTailLength());
            while (srcBuffer.hasRemaining()) {
                if (socketChannel.read(srcBuffer) == -1) {
                    return null;
                }
            }
            byte[] tail = new byte[dataPacket.getTailLength()];
            srcBuffer.flip().get(tail);
            dataPacket.setTail(decryptCipher.update(tail));
        }
        srcBuffer = bufferManager.getSrcBuffer(ImtpsSecretKey.TAG_LENGTH);
        while (srcBuffer.hasRemaining()) {
            if (socketChannel.read(srcBuffer) == -1) {
                return null;
            }
        }
        byte[] tag = new byte[ImtpsSecretKey.TAG_LENGTH];
        srcBuffer.flip().get(tag);
        decryptCipher.verifyTag(tag);
        return dataPacket;
    }
    public void writeDataPacket(SelectionKey selectionKey, ImtpsSecretKey imtpsSecretKey, DataPacket dataPacket) throws Exception {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        BufferManager bufferManager = BufferManager.get();
        ByteBuffer srcBuffer = bufferManager.getSrcBuffer(ImtpsSecretKey.NONCE_LENGTH + dataPacket.getCurrenHeaderLength());
        ByteBuffer dstBuffer = bufferManager.getDestBuffer(srcBuffer.limit() + ImtpsSecretKey.TAG_LENGTH);
        byte[] nonce = ImtpsSecretKey.createNonce();
        ImtpsSecretKey.EncryptCipher encryptCipher = imtpsSecretKey.createEncryptCipher(nonce);
        encryptCipher.doFinal(dataPacket.headerToBuffer(srcBuffer).flip(), dstBuffer.put(nonce)).flip();
        while (dstBuffer.hasRemaining()) {
            socketChannel.write(dstBuffer);
        }
        if (sendViewMap.get(dataPacket.getTaskId()) instanceof AbstractTransmitView transmitView) {
            if (transmitView.trySubmit()) {
                threadPool.submit(transmitView);
            }
            for (AbstractDataBody<?> body : dataPacket.getBodys()) {
                transmitView.setInfo(dataPacket.getPacketIndex(), body.getSize(), body.getMetadata());
                try {
                    body.prepareEncode();
                    transmitView.begin();
                    if (body.getMetaLength() > 0) {
                        dstBuffer = bufferManager.getDestBuffer(body.getMetaLength());
                        dstBuffer.put(encryptCipher.update(body.getMetadata())).flip();
                        while (dstBuffer.hasRemaining()) {
                            socketChannel.write(dstBuffer);
                        }
                    }
                    int bufferSize = (int) Math.min(body.getSize(), MAX_BUFFER_SIZE);
                    srcBuffer = bufferManager.getSrcBuffer(bufferSize);
                    dstBuffer = bufferManager.getDestBuffer(bufferSize);
                    for (long residue = body.getSize(), handleNumber; residue > 0; residue -= handleNumber) {
                        body.encode(srcBuffer.clear());
                        handleNumber = encryptCipher.update(srcBuffer.flip(), dstBuffer.clear());
                        dstBuffer.flip();
                        while (dstBuffer.hasRemaining()) {
                            socketChannel.write(dstBuffer);
                        }
                        transmitView.updateSize(handleNumber);
                    }
                    body.finishEncode();
                } finally {
                    body.release();
                    transmitView.finish();
                }
            }
        } else {
            for (AbstractDataBody<?> body : dataPacket.getBodys()) {
                try {
                    body.prepareEncode();
                    if (body.getMetaLength() > 0) {
                        dstBuffer = bufferManager.getDestBuffer(body.getMetaLength());
                        dstBuffer.put(encryptCipher.update(body.getMetadata())).flip();
                        while (dstBuffer.hasRemaining()) {
                            socketChannel.write(dstBuffer);
                        }
                    }
                    int bufferSize = (int) Math.min(body.getSize(), MAX_BUFFER_SIZE);
                    srcBuffer = bufferManager.getSrcBuffer(bufferSize);
                    dstBuffer = bufferManager.getDestBuffer(bufferSize);
                    for (long residue = body.getSize(), handleNumber; residue > 0; residue -= handleNumber) {
                        body.encode(srcBuffer.clear());
                        handleNumber = encryptCipher.update(srcBuffer.flip(), dstBuffer.clear());
                        dstBuffer.flip();
                        while (dstBuffer.hasRemaining()) {
                            socketChannel.write(dstBuffer);
                        }
                    }
                    body.finishEncode();
                } finally {
                    body.release();
                }
            }
        }
        if (dataPacket.getTailLength() > 0) {
            dstBuffer = bufferManager.getSrcBuffer(dataPacket.getTailLength());
            dstBuffer.put(encryptCipher.update(dataPacket.getTail())).flip();
            while (dstBuffer.hasRemaining()) {
                socketChannel.write(dstBuffer);
            }
        }
        dstBuffer = bufferManager.getDestBuffer(ImtpsSecretKey.TAG_LENGTH);
        dstBuffer.clear().put(encryptCipher.createTag()).flip();
        while (dstBuffer.hasRemaining()) {
            socketChannel.write(dstBuffer);
        }
    }

    public ControlPacket readControlPacket(SelectionKey selectionKey, ImtpsSecretKey imtpsSecretKey) throws Exception {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        BufferManager bufferManager = BufferManager.get();
        ByteBuffer srcBuffer = bufferManager.getSrcBuffer(ImtpsSecretKey.NONCE_LENGTH + ControlPacket.BASIC_HEADER_LENGTH + ImtpsSecretKey.TAG_LENGTH);
        while (srcBuffer.hasRemaining()) {
            if (socketChannel.read(srcBuffer) == -1) {
                return null;
            }
        }
        byte[] nonce = new byte[ImtpsSecretKey.NONCE_LENGTH];
        byte[] hander = new byte[ControlPacket.BASIC_HEADER_LENGTH];
        byte[] tag = new byte[ImtpsSecretKey.TAG_LENGTH];
        srcBuffer.flip().get(nonce).get(hander).get(tag);
        ImtpsSecretKey.DecryptCipher decryptCipher = imtpsSecretKey.createDecryptCipher(nonce);
        hander = decryptCipher.update(hander);
        if (!decryptCipher.verifyTag(tag)) {
            throw new SecurityException("MAC verification failed");
        }
        if (hander[1] != 0) {
            srcBuffer = bufferManager.getSrcBuffer(hander[1] + ImtpsSecretKey.TAG_LENGTH);
            while (srcBuffer.hasRemaining()) {
                if (socketChannel.read(srcBuffer) == -1) {
                    return null;
                }
            }
            byte[] content = new byte[hander[1]];
            srcBuffer.flip().get(content).get(tag);
            content = decryptCipher.update(content);
            if (!decryptCipher.verifyTag(tag)) {
                throw new SecurityException("MAC verification failed");
            }
            return new ControlPacket(hander[0], content);
        } else {
            return new ControlPacket(hander[0]);
        }
    }
    public void writeControlPacket(SelectionKey selectionKey, ImtpsSecretKey imtpsSecretKey, ControlPacket controlPacket) throws Exception {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        BufferManager bufferManager = BufferManager.get();
        ByteBuffer srcBuffer = bufferManager.getSrcBuffer(ImtpsSecretKey.NONCE_LENGTH + ControlPacket.BASIC_HEADER_LENGTH + ImtpsSecretKey.TAG_LENGTH);
        byte[] nonce = ImtpsSecretKey.createNonce();
        ImtpsSecretKey.EncryptCipher encryptCipher = imtpsSecretKey.createEncryptCipher(nonce);
        srcBuffer.put(nonce).put(encryptCipher.update(controlPacket.getHander())).put(encryptCipher.createTag()).flip();
        while (srcBuffer.hasRemaining()) {
            socketChannel.write(srcBuffer);
        }
        if (controlPacket.getSize() != 0) {
            srcBuffer = bufferManager.getSrcBuffer(controlPacket.getSize() + ImtpsSecretKey.TAG_LENGTH);
            srcBuffer.put(encryptCipher.update(controlPacket.getContent())).put(encryptCipher.createTag()).flip();
            while (srcBuffer.hasRemaining()) {
                socketChannel.write(srcBuffer);
            }
        }
    }
}
