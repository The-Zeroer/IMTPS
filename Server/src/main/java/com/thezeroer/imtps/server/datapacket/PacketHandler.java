package com.thezeroer.imtps.server.datapacket;

import com.thezeroer.imtps.server.buffer.BufferManager;
import com.thezeroer.imtps.server.datapacket.databody.AbstractDataBody;
import com.thezeroer.imtps.server.log.ImtpsLogger;
import com.thezeroer.imtps.server.process.task.ImtpsTask;
import com.thezeroer.imtps.server.security.ImtpsSecretKey;
import com.thezeroer.imtps.server.view.AbstractTransmitView;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * 包处理程序
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/07/14
 */
public class PacketHandler {
    private static final int MAX_BUFFER_SIZE = 64 * 1024;
    private static final int AES_HEADER_SIZE = ImtpsSecretKey.NONCE_LENGTH + DataPacket.BASIC_HEADER_SIZE + ImtpsSecretKey.TAG_LENGTH;
    private final ConcurrentHashMap<Long, Supplier<? extends AbstractDataBody<?>>> bodyMap;
    private final ConcurrentHashMap<String, AbstractTransmitView> sendViewMap, receiveViewMap;

    private ExecutorService threadPool;
    private final ImtpsLogger imtpsLogger;

    public PacketHandler(ImtpsLogger imtpsLogger) {
        bodyMap = new ConcurrentHashMap<>();
        sendViewMap = new ConcurrentHashMap<>();
        receiveViewMap = new ConcurrentHashMap<>();
        this.imtpsLogger = imtpsLogger;
    }

    public boolean registerDataBody(Supplier<? extends AbstractDataBody<?>> constructor) {
        return bodyMap.putIfAbsent(constructor.get().getId(), constructor) == null;
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
        ByteBuffer srcBuffer = bufferManager.getSrcBuffer(AES_HEADER_SIZE);
        ByteBuffer dstBuffer = bufferManager.getDestBuffer(DataPacket.BASIC_HEADER_SIZE);
        AbstractDataBody<?> dataBody = null;
        try {
            while (srcBuffer.hasRemaining()) {
                if (socketChannel.read(srcBuffer) == -1) {
                    return null;
                }
            }
            byte[] nonce = new byte[ImtpsSecretKey.NONCE_LENGTH];
            srcBuffer.flip().get(nonce);
            ImtpsSecretKey.DecryptCipher decryptCipher = imtpsSecretKey.createDecryptCipher(nonce);
            decryptCipher.doFinal(srcBuffer, dstBuffer);
            DataPacket dataPacket = DataPacket.setDataPacketBasicHeader(dstBuffer.flip());

            if (bodyMap.get(dataPacket.getDataBodyId()) instanceof Supplier<? extends AbstractDataBody<?>> supplier) {
                dataBody = supplier.get();
                if (receiveViewMap.get(dataPacket.getTaskId()) instanceof AbstractTransmitView receiveView) {
                    if (dataPacket.getMetadataLength() > 0) {
                        srcBuffer = bufferManager.getSrcBuffer(dataPacket.getMetadataLength());
                        while (srcBuffer.hasRemaining()) {
                            if (socketChannel.read(srcBuffer) == -1) {
                                return null;
                            }
                        }
                        byte[] metadata = new byte[dataPacket.getMetadataLength()];
                        srcBuffer.flip().get(metadata);
                        dataBody.setMetadata(decryptCipher.update(metadata));
                        receiveView.setMetadata(dataBody.getMetadata());
                    }
                    long dataBodySize = dataPacket.getDataBodySize();
                    receiveView.setSumSize(dataBodySize);
                    dataBody.prepareDecode(dataBodySize);
                    int bufferSize = (int) Math.min(dataBodySize, MAX_BUFFER_SIZE);
                    srcBuffer = bufferManager.getSrcBuffer(bufferSize);
                    dstBuffer = bufferManager.getDestBuffer(bufferSize);
                    receiveView.begin();
                    threadPool.submit(receiveView);
                    for (long residue = dataBodySize, handleNumber; residue > 0; residue -= handleNumber) {
                        if (residue < srcBuffer.clear().remaining()) {
                            srcBuffer.limit((int) residue);
                        }
                        while (srcBuffer.hasRemaining()) {
                            if (socketChannel.read(srcBuffer) == -1) {
                                return null;
                            }
                        }
                        handleNumber = decryptCipher.update(srcBuffer.flip(), dstBuffer.clear());
                        receiveView.updateSize(handleNumber);
                        dataBody.decode(dstBuffer.flip());
                        srcBuffer.clear();
                    }
                    receiveView.finish();
                } else {
                    if (dataPacket.getMetadataLength() > 0) {
                        srcBuffer = bufferManager.getSrcBuffer(dataPacket.getMetadataLength());
                        while (srcBuffer.hasRemaining()) {
                            if (socketChannel.read(srcBuffer) == -1) {
                                return null;
                            }
                        }
                        byte[] metadata = new byte[dataPacket.getMetadataLength()];
                        srcBuffer.flip().get(metadata);
                        dataBody.setMetadata(decryptCipher.update(metadata));
                    }
                    long dataBodySize = dataPacket.getDataBodySize();
                    dataBody.prepareDecode(dataBodySize);
                    int bufferSize = (int) Math.min(dataBodySize, MAX_BUFFER_SIZE);
                    srcBuffer = bufferManager.getSrcBuffer(bufferSize);
                    dstBuffer = bufferManager.getDestBuffer(bufferSize);
                    for (long residue = dataBodySize, handleNumber; residue > 0; residue -= handleNumber) {
                        if (residue < srcBuffer.remaining()) {
                            srcBuffer.limit((int) residue);
                        }
                        while (srcBuffer.hasRemaining()) {
                            if (socketChannel.read(srcBuffer) == -1) {
                                return null;
                            }
                        }
                        handleNumber = decryptCipher.update(srcBuffer.flip(), dstBuffer.clear());
                        dataBody.decode(dstBuffer.flip());
                        srcBuffer.clear();
                    }
                }
                if (dataPacket.getDataTailLength() > 0) {
                    srcBuffer = bufferManager.getSrcBuffer(dataPacket.getDataTailLength());
                    while (srcBuffer.hasRemaining()) {
                        if (socketChannel.read(srcBuffer) == -1) {
                            return null;
                        }
                    }
                    byte[] tail = new byte[dataPacket.getDataTailLength()];
                    srcBuffer.flip().get(tail);
                    dataPacket.setDataTail(decryptCipher.update(tail));
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
                dataBody.finishDecode();
                dataPacket.attachDataBody(dataBody);
            }
            return dataPacket;
        } finally {
            if (dataBody != null) {
                dataBody.release();
            }
        }
    }
    public void writeDataPacket(SelectionKey selectionKey, ImtpsSecretKey imtpsSecretKey, DataPacket dataPacket) throws Exception {
        if (dataPacket.getTaskId().isEmpty()) {
            dataPacket.setTaskId(ImtpsTask.createTaskId());
        }
        imtpsLogger.trace("发送DataPacket[$]", dataPacket);

        BufferManager bufferManager = BufferManager.get();
        ByteBuffer srcBuffer = bufferManager.getSrcBuffer(DataPacket.BASIC_HEADER_SIZE);
        ByteBuffer dstBuffer = bufferManager.getDestBuffer(AES_HEADER_SIZE);
        AbstractDataBody<?> dataBody = dataPacket.getDataBody();
        try {
            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            byte[] nonce = ImtpsSecretKey.createNonce();
            ImtpsSecretKey.EncryptCipher encryptCipher = imtpsSecretKey.createEncryptCipher(nonce);
            encryptCipher.doFinal(dataPacket.getDataPacketBasicHeader(srcBuffer).flip(), dstBuffer.put(nonce)).flip();
            while (dstBuffer.hasRemaining()) {
                socketChannel.write(dstBuffer);
            }

            if (dataBody != null) {
                dataBody.prepareEncode();
                if (sendViewMap.get(dataPacket.getTaskId()) instanceof AbstractTransmitView sendView) {
                    sendView.setSumSize(dataPacket.getDataBodySize());
                    if (dataPacket.getMetadataLength() > 0) {
                        sendView.setMetadata(dataBody.getMetadata());
                        dstBuffer = bufferManager.getDestBuffer(dataPacket.getMetadataLength());
                        dstBuffer.put(encryptCipher.update(dataBody.getMetadata())).flip();
                        while (dstBuffer.hasRemaining()) {
                            socketChannel.write(dstBuffer);
                        }
                    }
                    int bufferSize = (int) Math.min(dataPacket.getDataBodySize(), MAX_BUFFER_SIZE);
                    srcBuffer = bufferManager.getSrcBuffer(bufferSize);
                    dstBuffer = bufferManager.getDestBuffer(bufferSize);
                    sendView.begin();
                    threadPool.submit(sendView);
                    for (long residue = dataPacket.getDataBodySize(), handleNumber; residue > 0; residue -= handleNumber) {
                        dataBody.encode(srcBuffer.clear());
                        handleNumber = encryptCipher.update(srcBuffer.flip(), dstBuffer.clear());
                        dstBuffer.flip();
                        while (dstBuffer.hasRemaining()) {
                            socketChannel.write(dstBuffer);
                        }
                        sendView.updateSize(handleNumber);
                    }
                    sendView.finish();
                } else {
                    if (dataPacket.getMetadataLength() > 0) {
                        dstBuffer = bufferManager.getDestBuffer(dataPacket.getMetadataLength());
                        dstBuffer.put(encryptCipher.update(dataBody.getMetadata())).flip();
                        while (dstBuffer.hasRemaining()) {
                            socketChannel.write(dstBuffer);
                        }
                    }
                    int bufferSize = (int) Math.min(dataPacket.getDataBodySize(), MAX_BUFFER_SIZE);
                    srcBuffer = bufferManager.getSrcBuffer(bufferSize);
                    dstBuffer = bufferManager.getDestBuffer(bufferSize);
                    for (long residue = dataPacket.getDataBodySize(), handleNumber; residue > 0; residue -= handleNumber) {
                        dataBody.encode(srcBuffer.clear());
                        handleNumber = encryptCipher.update(srcBuffer.flip(), dstBuffer.clear());
                        dstBuffer.flip();
                        while (dstBuffer.hasRemaining()) {
                            socketChannel.write(dstBuffer);
                        }
                    }
                }
                dataBody.finishEncode();
                if (dataPacket.getDataTailLength() > 0) {
                    dstBuffer = bufferManager.getDestBuffer(dataPacket.getDataTailLength());
                    dstBuffer.put(encryptCipher.update(dataPacket.getDataTail())).flip();
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
        } finally {
            if (dataBody != null) {
                dataBody.release();
            }
        }
    }

    public ControlPacket readControlPacket(SelectionKey selectionKey, ImtpsSecretKey imtpsSecretKey) throws Exception {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        BufferManager bufferManager = BufferManager.get();
        ByteBuffer srcBuffer = bufferManager.getSrcBuffer(ImtpsSecretKey.NONCE_LENGTH + ControlPacket.BASIC_HEADER_SIZE + ImtpsSecretKey.TAG_LENGTH);
        while (srcBuffer.hasRemaining()) {
            if (socketChannel.read(srcBuffer) == -1) {
                return null;
            }
        }
        byte[] nonce = new byte[ImtpsSecretKey.NONCE_LENGTH];
        byte[] hander = new byte[ControlPacket.BASIC_HEADER_SIZE];
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
        ByteBuffer srcBuffer = bufferManager.getSrcBuffer(ImtpsSecretKey.NONCE_LENGTH + ControlPacket.BASIC_HEADER_SIZE + ImtpsSecretKey.TAG_LENGTH);
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
        imtpsLogger.trace("发送ControlPacket[$]", controlPacket);
    }
}
