package com.thezeroer.imtps.server.datapacket.databody;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

/**
 * 抽象数据体，如需自定义数据体可继承此类，实现子类时务必保留空参构造
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/06/29
 */
public abstract class AbstractDataBody<T> implements Cloneable{
    public enum TYPE {
        Basic, File,
    }

    private static final ConcurrentHashMap<Class<?>, Long> bodyMap = new ConcurrentHashMap<>();
    protected T data;
    protected byte[] metaData;

    /**
     * 编码，将此DataBody中的数据写入ByteBuffer中
     *
     * @param output 将数据写入此字节缓冲区
     */
    public abstract void encode(ByteBuffer output) throws Exception;
    /**
     * 解码，从ByteBuffer中读取数据填充此DataBody
     *
     * @param input 从此字节缓冲区中读取数据
     */
    public abstract void decode(ByteBuffer input) throws Exception;

    /**
     * 准备编码
     */
    public void prepareEncode() throws Exception {}
    /**
     * 完成编码
     */
    public void finishEncode() throws Exception {}
    /**
     * 准备解码
     *
     * @param size 需要解码的数据大小
     */
    public void prepareDecode(long size) throws Exception {}
    /**
     * 完成解码
     */
    public void finishDecode() throws Exception {}

    /**
     * 清理资源
     */
    public void release() {}

    public abstract TYPE getType();
    public abstract long getSize();

    /**
     * 获取元数据，在prepareEncode()之后及dataPacket.attachDataBody()时调用
     *
     * @return {@link byte[] }
     */
    public byte[] getMetadata() {
        return metaData;
    }
    /**
     * 设置元数据，在prepareDecode()之前调用
     *
     * @param metadata 元数据
     */
    public AbstractDataBody<?> setMetadata(byte[] metadata) {
        this.metaData = metadata;
        return this;
    }

    public final T getData() {
        return data;
    }
    public final long getId() {
        Class<?> clazz = getClass();
        if (bodyMap.get(clazz) instanceof Long dataBodyId) {
            return dataBodyId;
        } else {
            StringBuilder sb = new StringBuilder();
            CRC32 crc = new CRC32();
            long dataBodyId;
            do {
                sb.append(clazz.getSimpleName()); // 仅使用简单类名

                // 获取成员变量，并按名称排序
                Field[] fields = clazz.getDeclaredFields();
                Arrays.sort(fields, Comparator.comparing(Field::getName));
                for (Field field : fields) {
                    sb.append("_").append(field.getName());
                }

                // 获取当前类定义的方法（不包含继承的方法），按名称排序
                Method[] methods = clazz.getDeclaredMethods();
                Arrays.sort(methods, Comparator.comparing(Method::getName));
                for (Method method : methods) {
                    sb.append("_").append(method.getName());
                }

                // 计算 CRC32 哈希值（稳定、快速）
                crc.update(sb.toString().getBytes(StandardCharsets.UTF_8));
                dataBodyId = crc.getValue();
            } while (dataBodyId == 0);
            bodyMap.put(clazz, dataBodyId);
            return dataBodyId;
        }
    }

    @Override
    public AbstractDataBody<T> clone() {
        try {
            AbstractDataBody<T> copy = (AbstractDataBody<T>) super.clone();
            // 深拷贝可变字段
            if (this.metaData != null) {
                copy.metaData = this.metaData.clone();
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e); // 不可能发生
        }
    }
}
