package imtp.client.security;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
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
public class Secure {
    private final KeyPair keyPair;

    public Secure() throws NoSuchAlgorithmException {
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
        return new SecretKeySpec(keyAgreement.generateSecret(), 0, 16, "AES");
    }

    /**
     * 生成 IV
     */
    public static byte[] generateIv() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * 生成加密密码
     *
     * @param secretKey 密钥
     * @param ivParameterSpec iv 参数规格
     * @return {@link Cipher }
     */
    public static Cipher generateEncryptCipher(SecretKey secretKey, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
        return cipher;
    }

    /**
     * 生成解密密码
     *
     * @param secretKey 密钥
     * @param ivParameterSpec iv 参数规格
     * @return {@link Cipher }
     */
    public static Cipher generateDecryptCipher(SecretKey secretKey, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
        return cipher;
    }
}
