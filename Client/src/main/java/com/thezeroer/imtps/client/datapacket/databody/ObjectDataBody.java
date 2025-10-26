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
    public ObjectDataBody clone() {
        ObjectDataBody copy = (ObjectDataBody) super.clone(); // 克隆父类部分（data、metaData等）
        // 深拷贝当前类字段
        if (this.bytes != null) {
            copy.bytes = this.bytes.clone();
        }
        // 尝试深拷贝 data（如果是可序列化对象）
        if (this.data instanceof Serializable) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(this.data);
                try (ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
                     ObjectInputStream ois = new ObjectInputStream(bis)) {
                    copy.data = ois.readObject(); // 得到完全独立的对象副本
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                // 若无法序列化，回退到浅拷贝
                copy.data = this.data;
            }
        } else {
            // 非序列化对象：只能浅拷贝
            copy.data = this.data;
        }
        copy.size = this.size;
        copy.position = this.position;
        return copy;
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
