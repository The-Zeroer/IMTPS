package imtp.server.datapacket;

import imtp.server.datapacket.databody.AbstractDataBody;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

/**
 * 数据体管理器
 *
 * @author NiZhanBo
 * @since 2025/02/25
 * @version 1.0.0
 */
public class DataBodyManager {
    private static final ConcurrentHashMap<Long, AbstractDataBody<?>> idToClassMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<AbstractDataBody<?>, Long> classToIdMap = new ConcurrentHashMap<>();

    public static boolean addDataBody(AbstractDataBody<?> dataBody) {
        long classId = getClassId(dataBody);
        if (idToClassMap.putIfAbsent(classId, dataBody) == null) {
            classToIdMap.put(dataBody, classId);
            return true;
        } else {
            return false;
        }
    }
    public static boolean removeDataBody(AbstractDataBody<?> dataBody) {
        long classId = getClassId(dataBody);
        if (idToClassMap.remove(classId) != null) {
            classToIdMap.remove(dataBody);
            return true;
        } else {
            return false;
        }
    }
    public static AbstractDataBody<?> getDataBody(long classId) {
        if (idToClassMap.get(classId) instanceof AbstractDataBody<?> dataBody) {
            return dataBody.createNewInstance();
        } else {
            return null;
        }
    }

    public static long getClassId(AbstractDataBody<?> dataBody) {
        Long classId = classToIdMap.get(dataBody);
        if (classId != null) {
            return classId;
        }

        Class<? extends AbstractDataBody> clazz = dataBody.getClass();
        StringBuilder sb = new StringBuilder();
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
        CRC32 crc = new CRC32();
        crc.update(sb.toString().getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }
}