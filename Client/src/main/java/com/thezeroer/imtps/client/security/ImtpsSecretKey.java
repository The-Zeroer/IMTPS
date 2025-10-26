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

        public int update(ByteBuffer srcBuffer, ByteBuffer dstBuffer) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
            int position = dstBuffer.position();
            int updateNumber = cipher.doFinal(srcBuffer, dstBuffer.clear());
            mac.update(dstBuffer.flip().position(position));
            return updateNumber;
        }
        public byte[] update(byte[] srcBytes) throws IllegalBlockSizeException, BadPaddingException {
            byte[] encrypted = cipher.doFinal(srcBytes);
            mac.update(encrypted);
            return encrypted;
        }

        public ByteBuffer doFinal(ByteBuffer srcBuffer, ByteBuffer dstBuffer) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
            int position = dstBuffer.position();
            cipher.doFinal(srcBuffer, dstBuffer);
            mac.update(dstBuffer.flip().position(position));
            dstBuffer.limit(dstBuffer.limit() + TAG_LENGTH).put(mac.doFinal());
            return dstBuffer;
        }

        public EncryptCipher attach(byte[] bytes) {
            mac.update(bytes);
            return this;
        }
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

        public int update(ByteBuffer srcBuffer, ByteBuffer dstBuffer) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
            int position = srcBuffer.position();
            mac.update(srcBuffer);
            return cipher.doFinal(srcBuffer.flip().position(position), dstBuffer);
        }
        public byte[] update(byte[] srcBytes) throws IllegalBlockSizeException, BadPaddingException {
            mac.update(srcBytes);
            return cipher.doFinal(srcBytes);
        }

        public ByteBuffer doFinal(ByteBuffer srcBuffer, ByteBuffer dstBuffer) throws IllegalBlockSizeException, BadPaddingException, ShortBufferException {
            int position = srcBuffer.position();
            byte[] encrypted = new byte[srcBuffer.remaining() - TAG_LENGTH];
            byte[] tag = new byte[TAG_LENGTH];
            srcBuffer.get(encrypted).get(tag);
            mac.update(encrypted);
            if (Arrays.equals(mac.doFinal(), tag)) {
                cipher.doFinal(srcBuffer.position(position).limit(position + encrypted.length), dstBuffer);
            } else {
                throw new SecurityException("MAC verification failed");
            }
            return dstBuffer;
        }

        public DecryptCipher attach(byte[] bytes) {
            mac.update(bytes);
            return this;
        }
        public boolean verifyTag(byte[] tag) {
            if (Arrays.equals(mac.doFinal(), tag)) {
                return true;
            } else {
                throw new SecurityException("MAC verification failed");
            }
        }
    }
}
