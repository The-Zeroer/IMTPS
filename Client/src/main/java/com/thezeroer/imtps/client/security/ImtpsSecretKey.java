package com.thezeroer.imtps.client.security;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class ImtpsSecretKey {
    public final static String DH_ALGORITHM = "X25519";
    public final static String AES_ALGORITHM = "AES/CTR/NoPadding";
    public final static String MAC_ALGORITHM = "HmacSHA256";
    public final static int DH_LENGTH = 44;
    public final static int FINISHEDMESSAGE_LENGTH = 32;
    public final static int NONCE_LENGTH = 16;
    public final static int TAG_LENGTH = 32;

    private final SecretKey aesKey, macKey;

    public ImtpsSecretKey(PrivateKey privateKey, byte[] publickey) throws NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        KeyAgreement keyAgreement = KeyAgreement.getInstance(DH_ALGORITHM);
        keyAgreement.init(privateKey);
        keyAgreement.doPhase(KeyFactory.getInstance(DH_ALGORITHM).generatePublic(new X509EncodedKeySpec(publickey)), true);
        byte[] derived = MessageDigest.getInstance("SHA-512").digest(keyAgreement.generateSecret());
        aesKey = new SecretKeySpec(derived, 0, 32, "AES");
        macKey = new SecretKeySpec(derived, 32, 32, "HmacSHA256");
    }

    /**
     * 创建加密密码
     *
     * @param nonce 随机数
     * @return {@link EncryptCipher }
     */
    public EncryptCipher createEncryptCipher(byte[] nonce) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(nonce));
        Mac mac = Mac.getInstance(MAC_ALGORITHM);
        mac.init(macKey);
        return new EncryptCipher(cipher, mac).attach(nonce);
    }
    /**
     * 创建解密密码
     *
     * @param nonce 随机数
     * @return {@link DecryptCipher }
     */
    public DecryptCipher createDecryptCipher(byte[] nonce) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(nonce));
        Mac mac = Mac.getInstance(MAC_ALGORITHM);
        mac.init(macKey);
        return new DecryptCipher(cipher, mac).attach(nonce);
    }

    /**
     * 加密
     */
    public byte[] encrypt(byte[] srcBytes) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] nonce = createNonce();
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(nonce));
        Mac mac = Mac.getInstance(MAC_ALGORITHM);
        mac.init(macKey);
        byte[] encrypted = cipher.doFinal(srcBytes);
        mac.update(nonce);
        mac.update(encrypted);
        byte[] dstBytes = new byte[NONCE_LENGTH + encrypted.length + TAG_LENGTH];
        System.arraycopy(nonce, 0, dstBytes, 0, NONCE_LENGTH);
        System.arraycopy(encrypted, 0, dstBytes, NONCE_LENGTH, encrypted.length);
        System.arraycopy(mac.doFinal(), 0, dstBytes, dstBytes.length - TAG_LENGTH, TAG_LENGTH);
        return dstBytes;
    }
    /**
     * 解密
     */
    public byte[] decrypt(byte[] srcBytes) throws SecurityException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] nonce = Arrays.copyOfRange(srcBytes, 0, NONCE_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(srcBytes, NONCE_LENGTH, srcBytes.length - TAG_LENGTH);
        byte[] tag = Arrays.copyOfRange(srcBytes, srcBytes.length - TAG_LENGTH, srcBytes.length);
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(nonce));
        Mac mac = Mac.getInstance(MAC_ALGORITHM);
        mac.init(macKey);
        mac.update(nonce);
        mac.update(encrypted);
        if (!Arrays.equals(mac.doFinal(), tag)) {
            throw new SecurityException("MAC verification failed");
        }
        return cipher.doFinal(encrypted);
    }

    public byte[] getFinishedMessage() throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(MAC_ALGORITHM);
        mac.init(macKey);
        return mac.doFinal(aesKey.getEncoded());
    }

    public static byte[] createNonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }
    public static KeyPair createKeyPair() throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance(DH_ALGORITHM).generateKeyPair();
    }

    public static class EncryptCipher {
        private final Cipher cipher;
        private final Mac mac;

        public EncryptCipher(Cipher cipher, Mac mac) {
            this.cipher = cipher;
            this.mac = mac;
        }

        /**
         * 流式加密：对 src 中的可用字节执行 cipher.update，结果写入 dst。
         * 要求：
         *  - 调用前：src.position() 指向可读开始，src.limit() 指向可读结束；
         *  - dst 必须有足够剩余空间写入输出（至少 cipher.update 可能写入的字节数）。
         *
         * 返回写入到 dst 的字节数（与 cipher.update 返回一致）。
         */
        public int update(ByteBuffer src, ByteBuffer dst) throws ShortBufferException {
            // 记录 dst 写入前的位置
            int dstStart = dst.position();
            // 执行流式加密（不会 finalize）
            int written = cipher.update(src, dst); // advances src.position() and dst.position()
            if (written > 0) {
                int oldPos = dst.position();
                int oldLimit = dst.limit();
                // 让 MAC 看到刚刚写入的那一段
                dst.position(dstStart);
                dst.limit(dstStart + written);
                mac.update(dst); // Mac 会从 position → limit 读取数据
                // 恢复 dst
                dst.position(oldPos);
                dst.limit(oldLimit);
            }
            return written;
        }

        /**
         * 对字节数组流式加密（方便小块）。
         */
        public byte[] update(byte[] srcBytes) {
            byte[] out = cipher.update(srcBytes);
            if (out != null && out.length > 0) mac.update(out);
            return out;
        }

        /**
         * 最终化（结束分段流）：
         * - 对 src 中剩余的明文执行 doFinal，写到 dst；
         * - 把新增密文部分更新到 MAC；
         * - 在 dst 的末尾写入 MAC tag（mac.doFinal()）。
         *
         * 要求：
         *  - dst 必须至少剩余 (cipher.doFinal 可能写的字节数 + TAG_LENGTH) 空间。
         *  - 返回值是写好 tag 后的 dst（位置已位于写入结束之后，调用者可 flip/read）。
         */
        public ByteBuffer doFinal(ByteBuffer src, ByteBuffer dst) throws GeneralSecurityException {
            int dstStart = dst.position();
            // doFinal 把剩余明文加密并写入 dst
            cipher.doFinal(src, dst); // advances src and dst
            int dstAfterCipher = dst.position();
            int written = dstAfterCipher - dstStart;
            if (written > 0) {
                // 保存现场
                int oldPos = dst.position();
                int oldLimit = dst.limit();
                // 让 MAC 仅看到刚写入的密文部分
                dst.position(dstStart);
                dst.limit(dstAfterCipher);
                mac.update(dst);
                // 恢复现场
                dst.position(oldPos);
                dst.limit(oldLimit);
            }
            // 产生 tag 并写入 dst
            byte[] tag = mac.doFinal();
            dst.put(tag);
            return dst;
        }

        /**
         * 把附加数据（例如 nonce/header）加入 MAC 计算（在加密任何密文之前调用）。
         * 返回 this 以便链式调用。
         */
        public EncryptCipher attach(byte[] additional) {
            if (additional != null && additional.length > 0) mac.update(additional);
            return this;
        }

        /**
         * 如果需要单独取 tag（比如先计算后写入），调用此方法（会重置 mac 内部状态）。
         */
        public byte[] createTag() {
            return mac.doFinal();
        }
    }
    public static class DecryptCipher {
        private final Cipher cipher;
        private final Mac mac;

        public DecryptCipher(Cipher cipher, Mac mac) {
            this.cipher = cipher;
            this.mac = mac;
        }

        /**
         * 流式解密（用于中间分片，不包含最终的 tag）：
         * - 在调用之前，src 中必须包含纯密文片段（不包含 tag）且 position/limit 设置正确；
         * - 方法会先把被消费的 ciphertext 部分更新到 MAC（与加密端一致），再解密写入 dst。
         *
         * 返回写入 dst 的字节数。
         */
        public int update(ByteBuffer src, ByteBuffer dst) throws ShortBufferException {
            int srcStart = src.position();
            // 执行 cipher.update：会消费 src 并产出明文到 dst
            int written = cipher.update(src, dst);
            int srcAfter = src.position();
            if (srcAfter > srcStart) {
                int oldPos = src.position();
                int oldLimit = src.limit();
                src.position(srcStart);
                src.limit(srcAfter);
                mac.update(src);
                src.position(oldPos);
                src.limit(oldLimit);
            }
            return written;
        }

        /**
         * byte[] 版本的简单 update。
         */
        public byte[] update(byte[] srcBytes) {
            // 先更新 MAC（ciphertext）
            mac.update(srcBytes);
            // 然后解密
            return cipher.update(srcBytes);
        }

        /**
         * 最终化并验证 tag：
         * - srcBuffer 必须包含 [ciphertext || tag]，position 指向 ciphertext 开头，limit 指向 tag 末尾；
         * - 方法会把 ciphertext 部分用于 MAC 验证（同时不会移动调用者的 srcBuffer 的 position 之外，只会消费 srcBuffer），
         *   验证成功后再解密 ciphertext 写入 dst。
         *
         * 返回写入了明文的 dst（position 已在写入后）。
         *
         * 重要：此处先验证 MAC（使用 ciphertext + 已 attach 的附加数据），通过后才解密。
         */
        public ByteBuffer doFinal(ByteBuffer src, ByteBuffer dst) throws GeneralSecurityException {
            int total = src.remaining();
            if (total < TAG_LENGTH) {
                throw new SecurityException("not enough data for tag");
            }
            int encStart = src.position();
            int encEnd = encStart + (total - TAG_LENGTH);
            int oldLimit = src.limit();
            src.limit(encEnd);
            mac.update(src);
            byte[] receivedTag = new byte[TAG_LENGTH];
            src.limit(oldLimit).position(encEnd).get(receivedTag);
            if (!MessageDigest.isEqual(mac.doFinal(), receivedTag)) {
                throw new SecurityException("MAC verification failed");
            }
            cipher.doFinal(src.limit(encEnd).position(encStart), dst); // 解密只处理密文
            src.limit(oldLimit).position(encEnd + TAG_LENGTH); // 消费掉密文 + tag
            return dst;
        }

        public DecryptCipher attach(byte[] additional) {
            if (additional != null && additional.length > 0) mac.update(additional);
            return this;
        }

        /**
         * 如果需要单独验证 tag（tag 已由外部读取），可使用此方法（会消耗 mac 内部并抛异常或返回 true）。
         */
        public boolean verifyTag(byte[] tag) {
            byte[] computed = mac.doFinal();
            if (Arrays.equals(computed, tag)) {
                return true;
            } else {
                throw new SecurityException("MAC verification failed");
            }
        }
    }

//    public static class EncryptCipher {
//        private final Cipher cipher;
//        private final Mac mac;
//
//        public EncryptCipher(Cipher cipher, Mac mac) {
//            this.cipher = cipher;
//            this.mac = mac;
//        }
//
//        public int update(ByteBuffer srcBuffer, ByteBuffer dstBuffer) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
//            int position = dstBuffer.position();
//            int updateNumber = cipher.doFinal(srcBuffer, dstBuffer.clear());
//            mac.update(dstBuffer.flip().position(position));
//            return updateNumber;
//        }
//        public byte[] update(byte[] srcBytes) throws IllegalBlockSizeException, BadPaddingException {
//            byte[] encrypted = cipher.doFinal(srcBytes);
//            mac.update(encrypted);
//            return encrypted;
//        }
//
//        public ByteBuffer doFinal(ByteBuffer srcBuffer, ByteBuffer dstBuffer) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
//            int position = dstBuffer.position();
//            cipher.doFinal(srcBuffer, dstBuffer);
//            mac.update(dstBuffer.flip().position(position));
//            dstBuffer.limit(dstBuffer.limit() + TAG_LENGTH).put(mac.doFinal());
//            return dstBuffer;
//        }
//
//        public EncryptCipher attach(byte[] bytes) {
//            mac.update(bytes);
//            return this;
//        }
//        public byte[] createTag() {
//            return mac.doFinal();
//        }
//    }
//    public static class DecryptCipher {
//        private final Cipher cipher;
//        private final Mac mac;
//
//        public DecryptCipher(Cipher cipher, Mac mac) {
//            this.cipher = cipher;
//            this.mac = mac;
//        }
//
//        public int update(ByteBuffer srcBuffer, ByteBuffer dstBuffer) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
//            int position = srcBuffer.position();
//            mac.update(srcBuffer);
//            return cipher.doFinal(srcBuffer.flip().position(position), dstBuffer);
//        }
//        public byte[] update(byte[] srcBytes) throws IllegalBlockSizeException, BadPaddingException {
//            mac.update(srcBytes);
//            return cipher.doFinal(srcBytes);
//        }
//
//        public ByteBuffer doFinal(ByteBuffer srcBuffer, ByteBuffer dstBuffer) throws IllegalBlockSizeException, BadPaddingException, ShortBufferException {
//            int position = srcBuffer.position();
//            byte[] encrypted = new byte[srcBuffer.remaining() - TAG_LENGTH];
//            byte[] tag = new byte[TAG_LENGTH];
//            srcBuffer.get(encrypted).get(tag);
//            mac.update(encrypted);
//            if (Arrays.equals(mac.doFinal(), tag)) {
//                cipher.doFinal(srcBuffer.position(position).limit(position + encrypted.length), dstBuffer);
//            } else {
//                throw new SecurityException("MAC verification failed");
//            }
//            return dstBuffer;
//        }
//
//        public DecryptCipher attach(byte[] bytes) {
//            mac.update(bytes);
//            return this;
//        }
//        public boolean verifyTag(byte[] tag) {
//            if (Arrays.equals(mac.doFinal(), tag)) {
//                return true;
//            } else {
//                throw new SecurityException("MAC verification failed");
//            }
//        }
//    }
}
