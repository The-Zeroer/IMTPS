package com.thezeroer.imtps.server.datapacket;

import com.thezeroer.imtps.server.buffer.BufferManager;
import com.thezeroer.imtps.server.datapacket.databody.AbstractDataBody;
import com.thezeroer.imtps.server.process.TaskWrap;
import com.thezeroer.imtps.server.security.ImtpsSecretKey;
import com.thezeroer.imtps.server.view.AbstractTransmitView;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
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
    private final ConcurrentHashMap<String, TaskWrap> taskWrapMap;

    private ExecutorService threadPool;

    public PacketCodec() {
        bodyMap = new ConcurrentHashMap<>();
        taskWrapMap = new ConcurrentHashMap<>();
    }

    public void registerDataBody(Supplier<? extends AbstractDataBody<?>> constructor) {
        bodyMap.putIfAbsent(constructor.get().getId(), constructor);
    }
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public void addTaskWrap(TaskWrap taskWrap) {
        taskWrapMap.put(taskWrap.getTask().getTaskId(), taskWrap);
    }
    public void removeTaskWrap(TaskWrap taskWrap) {
        taskWrapMap.remove(taskWrap.getTask().getTaskId());
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
            TaskWrap taskWrap = taskWrapMap.get(dataPacket.getTaskId());
            AbstractDataBody<?>[] bodys = new AbstractDataBody[dataPacket.getBodyCount()];
            for (int i = 0; i < dataPacket.getBodyCount(); i++) {
                if (bodyMap.get(dataPacket.getBodyId(i)) instanceof Supplier<? extends AbstractDataBody<?>> supplier) {
                    ProgressUpdater updater = NO_OP_UPDATER;
                    if (taskWrap != null && taskWrap.hasTransmitView() && taskWrap.getReceiveTransmitView(dataPacket.getPacketIndex()) instanceof AbstractTransmitView transmitView) {
                        updater = forTransmitView(transmitView);
                    }
                    if (decodeDataBody(supplier.get(), dataPacket, (byte) i, socketChannel, bufferManager, decryptCipher, updater) instanceof AbstractDataBody<?> body) {
                        bodys[i] = body;
                    } else {
                        return null;
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
        ByteBuffer srcBuffer = bufferManager.getSrcBuffer(dataPacket.getCurrenHeaderLength());
        ByteBuffer dstBuffer = bufferManager.getDestBuffer(ImtpsSecretKey.NONCE_LENGTH + srcBuffer.limit() + ImtpsSecretKey.TAG_LENGTH);
        byte[] nonce = ImtpsSecretKey.createNonce();
        ImtpsSecretKey.EncryptCipher encryptCipher = imtpsSecretKey.createEncryptCipher(nonce);
        encryptCipher.doFinal(dataPacket.headerToBuffer(srcBuffer).flip(), dstBuffer.put(nonce)).flip();
        while (dstBuffer.hasRemaining()) {
            socketChannel.write(dstBuffer);
        }
        TaskWrap taskWrap = dataPacket.getTaskWrap();
        for (AbstractDataBody<?> body : dataPacket.getBodys()) {
            ProgressUpdater updater = NO_OP_UPDATER;
            if (taskWrap != null && taskWrap.hasTransmitView() && taskWrap.getSendTransmitView(dataPacket.getPacketIndex()) instanceof AbstractTransmitView transmitView) {
                updater = forTransmitView(transmitView);
            }
            encryptDataBody(body, socketChannel, bufferManager, encryptCipher, updater);
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
    private AbstractDataBody<?> decodeDataBody(AbstractDataBody<?> body, DataPacket dataPacket, byte packetIndex, SocketChannel socketChannel, BufferManager bufferManager, ImtpsSecretKey.DecryptCipher decryptCipher, ProgressUpdater updater) throws Exception {
        ByteBuffer srcBuffer, dstBuffer;
        try {
            int bodyMeatLength  = dataPacket.getBodyMeatLength(packetIndex);
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
            long bodySize = dataPacket.getBodySize(packetIndex);
            updater.begin(bodySize, body.getMetadata());
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
                updater.update(handleNumber);
            }
            body.finishDecode();
            return body;
        } finally {
            body.release();
            updater.finish();
        }
    }
    private void encryptDataBody(AbstractDataBody<?> body, SocketChannel socketChannel, BufferManager bufferManager, ImtpsSecretKey.EncryptCipher encryptCipher, ProgressUpdater updater) throws Exception {
        ByteBuffer srcBuffer, dstBuffer;
        try {
            updater.begin(body.getSize(), body.getMetadata());
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
                updater.update(handleNumber);
            }
            body.finishEncode();
        } finally {
            body.release();
            updater.finish();
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

    private interface ProgressUpdater {
        void begin(long totalBytes, byte[] metadata);
        void update(long handledBytes);
        void finish();
    }
    private static final ProgressUpdater NO_OP_UPDATER = new ProgressUpdater() {
        @Override public void begin(long totalBytes, byte[] metadata) {}
        @Override public void update(long handledBytes) {}
        @Override public void finish() {}
    };
    private ProgressUpdater forTransmitView(AbstractTransmitView transmitView) {
        return new ProgressUpdater() {
            @Override
            public void begin(long totalBytes, byte[] metadata) {
                transmitView.setInfo(totalBytes, metadata);
                transmitView.begin();
                threadPool.submit(transmitView);
            }
            @Override
            public void update(long handledBytes) {
                transmitView.updateSize(handledBytes);
            }
            @Override
            public void finish() {
                transmitView.finish();
            }
        };
    }
}
