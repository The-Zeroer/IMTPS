package imtps.server.security;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

/**
 * 安全
 *
 * @author NiZhanBo
 * @since 2025/03/12
 * @version 1.0.0
 */
public class SecureManager {
    public final static String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    public final static int IV_LENGTH = 16;
    private final KeyPair keyPair;

    public SecureManager() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DH");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();
    }

    /**
     * 获取公钥
     *
     * @return {@link PublicKey }
     */
    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    /**
     * 生成密钥
     *
     * @param clientKey 客户端密钥
     * @return {@link SecretKey }
     */
    public SecretKey generateKey(byte[] clientKey) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        KeyAgreement keyAgreement = KeyAgreement.getInstance("DH");
        keyAgreement.init(keyPair.getPrivate());
        keyAgreement.doPhase(KeyFactory.getInstance("DH").generatePublic(new X509EncodedKeySpec(clientKey)), true);
        return new SecretKeySpec(keyAgreement.generateSecret(), 0, 32, "AES");
    }
    /**
     * 生成 Finished 消息
     *
     * @param secretKey 密钥
     * @return {@link byte[] }
     * @throws NoSuchAlgorithmException 没有这样算法例外
     @throws InvalidKeyException 无效密钥异常
     */
    public static byte[] generateFinishedMessage(SecretKey secretKey) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getEncoded(), "HmacSHA256"));
        return mac.doFinal(secretKey.getEncoded());
    }

    /**
     * 生成 IV
     */
    public static byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
    /**
     * 生成加密密码
     *
     * @param secretKey 密钥
     * @param iv 参数规格
     * @return {@link Cipher }
     */
    public static Cipher generateEncryptCipher(SecretKey secretKey, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
        return cipher;
    }
    /**
     * 生成解密密码
     *
     * @param secretKey 密钥
     * @param iv 参数规格
     * @return {@link Cipher }
     */
    public static Cipher generateDecryptCipher(SecretKey secretKey, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
        return cipher;
    }

    public static int getBefitSize(int size) {
        return size <= 0 ? 0 : size + (16 - size % 16);
    }
    public static long getBefitSize(long size) {
        return size <= 0 ? 0 : size + (16 - size % 16);
    }
}
