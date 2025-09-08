package com.thezeroer.imtps.client.datapacket.databody;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * 对象数据正文
 *
 * @author NiZhanBo
 * @since 2025/08/12
 * @version 1.0.0
 */
public class ObjectDataBody extends AbstractDataBody<Object> {
    private int size, position;
    private byte[] bytes;

    public ObjectDataBody() {}
    public ObjectDataBody(Object object) throws IOException {
        data = object;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(object);
            bytes = bos.toByteArray();
            size = Math.toIntExact(bytes.length);
        }
    }

    @Override
    public void encode(ByteBuffer output) {
        int handleNumber = Math.min(size - position, output.remaining());
        output.put(bytes, position, handleNumber);
        position += handleNumber;
    }
    @Override
    public void decode(ByteBuffer input) {
        int handleNumber = Math.min(size - position, input.remaining());
        input.get(bytes, position, handleNumber);
        position += handleNumber;
    }

    @Override
    public void prepareDecode(long size) {
        this.size = Math.toIntExact(size);
        bytes = new byte[this.size];
    }
    @Override
    public void finishDecode() throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            data = ois.readObject();
        }
    }

    @Override
    public TYPE getType() {
        return size < 1024 * 1024 * 64 ? TYPE.Basic : TYPE.File;
    }
    @Override
    public long getSize() {
        return size;
    }

    @Override
    public String toString() {
        if (data != null) {
            return data.toString();
        } else {
            return super.toString();
        }
    }
}
