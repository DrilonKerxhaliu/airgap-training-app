package it.egeos.cut3g.airgap.service.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256 encryption for manifest.
 * Uses AES/GCM/NoPadding for authenticated encryption.
 */
@Service
public class CryptoService {

    private static final String ALGO = "AES";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final byte[] key;

    public CryptoService(@Value("${airgap.crypto.aes256.key.base64}") String keyBase64) {
        byte[] decoded = Base64.getDecoder().decode(keyBase64);
        if (decoded.length != 32) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes (Base64 decoded).");
        }
        this.key = decoded;
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORM);
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] ct = cipher.doFinal(plain);

            // output = iv || ciphertext
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Unable to encrypt payload", e);
        }
    }

    public byte[] decrypt(byte[] cipherBlob) {
        try {
            if (cipherBlob.length <= IV_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted payload (too short).");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] ct = new byte[cipherBlob.length - IV_BYTES];
            System.arraycopy(cipherBlob, 0, iv, 0, IV_BYTES);
            System.arraycopy(cipherBlob, IV_BYTES, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(TRANSFORM);
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGO);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));

            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new RuntimeException("Unable to decrypt payload", e);
        }
    }
}
