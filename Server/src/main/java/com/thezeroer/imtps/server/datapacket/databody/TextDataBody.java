package com.thezeroer.imtps.server.datapacket.databody;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本数据正文
 *
 * @author NiZhanBo
 * @version 1.0.0
 * @since 2025/06/29
 */
public class TextDataBody extends AbstractDataBody<String> {
    private int size, position;
    private byte[] dataBytes;

    public TextDataBody() {

    }
    public TextDataBody(String data) {
        this.data = data;
        this.dataBytes = data.getBytes(StandardCharsets.UTF_8);
        this.size = dataBytes.length;
    }

    public boolean isEmpty() {
        return dataBytes.length == 0;
    }

    public void attach(String attach) {
        metaData = attach.getBytes(StandardCharsets.UTF_8);
    }
    public String attachment() {
        if (metaData == null) {
            return null;
        } else {
            return new String(metaData, StandardCharsets.UTF_8);
        }
    }

    public static TextDataBody fromList(List<String> list) {
        return new TextDataBody(stringFromList(list));
    }
    public static List<String> toList(TextDataBody textDataBody) {
        return stringToList(textDataBody.getData());
    }

    public static TextDataBody fromMap(Map<String, String> map) {
        StringBuilder result = new StringBuilder("{");
        for (Map.Entry<String, String> entry : map.entrySet()) {
            result.append("\"").append(escape(entry.getKey())).append("\":")
                    .append("\"").append(escape(entry.getValue())).append("\"").append(",");
        }
        return new TextDataBody(result.deleteCharAt(result.length() - 1).append("}").toString());
    }
    public static Map<String, String> toMap(TextDataBody textDataBody) {
        Map<String, String> result = new HashMap<>();
        Matcher matcher = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(textDataBody.getData());
        while (matcher.find()) {
            result.put(unescape(matcher.group(1)), unescape(matcher.group(2)));
        }
        return result;
    }

    public static TextDataBody fromListArray(List<String[]> listArray) {
        List<String> result = new ArrayList<>();
        for (String[] array : listArray) {
            result.add(stringFromList(Arrays.asList(array)));
        }
        return new TextDataBody(String.join(";", result));
    }
    public static List<String[]> toListArray(TextDataBody textDataBody) {
        List<String[]> result = new ArrayList<>();
        String[] rows = textDataBody.getData().split("(?<!\\\\);");
        for (String row : rows) {
            result.add(stringToList(row).toArray(new String[0]));
        }
        return result;
    }

    @Override
    public void encode(ByteBuffer output) {
        int handleNumber = Math.min(size - position, output.remaining());
        output.put(dataBytes, position, handleNumber);
        position += handleNumber;
    }
    @Override
    public void decode(ByteBuffer input) {
        int handleNumber = Math.min(size - position, input.remaining());
        input.get(dataBytes, position, handleNumber);
        position += handleNumber;
    }

    @Override
    public void prepareDecode(long size) {
        this.size = Math.toIntExact(size);
        dataBytes = new byte[this.size];
    }
    @Override
    public void finishDecode() {
        data = new String(dataBytes, StandardCharsets.UTF_8);
    }

    @Override
    public TYPE getType() {
        return TYPE.Basic;
    }
    @Override
    public long getSize() {
        return size;
    }

    public static String stringFromList(List<String> list) {
        StringBuilder result = new StringBuilder("[");
        for (String string : list) {
            result.append("\"").append(escape(string)).append("\"").append(",");
        }
        return result.deleteCharAt(result.length() - 1).append("]").toString();
    }
    public static List<String> stringToList(String string) {
        List<String> result = new ArrayList<>();
        if (string != null && !string.isEmpty()) {
            Pattern pattern = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
            Matcher matcher = pattern.matcher(string);
            while (matcher.find()) {
                result.add(unescape(matcher.group(1)));
            }
        }
        return result;
    }
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace(";", "\\;");
    }
    private static String unescape(String s) {
        return s.replaceAll("\\\\([\"\\\\;])", "$1");
    }

    @Override
    public TextDataBody clone() {
        TextDataBody clone = (TextDataBody) super.clone(); // 返回 TextDataBody 实例
        if (dataBytes != null) {
            clone.dataBytes = dataBytes.clone(); // 深拷贝
        }
        clone.size = this.size;
        clone.position = this.position;
        return clone;
    }
    @Override
    public String toString() {
        if (data != null) {
            return data;
        } else {
            return super.toString();
        }
    }
}
