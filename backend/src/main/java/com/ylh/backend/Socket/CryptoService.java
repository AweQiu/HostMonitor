package com.ylh.backend.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    private static final String ECDH_ALGORITHM = "X25519";
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String HKDF_SALT = "hostmonitor-session";

    private KeyPair keyPair;
    private SecretKeySpec aesKey;

    public CryptoService() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(ECDH_ALGORITHM);
            keyPair = kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate ECDH key pair", e);
        }
    }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    public void deriveAesKey(String peerPublicKeyBase64) {
        try {
            byte[] peerEncoded = Base64.getDecoder().decode(peerPublicKeyBase64);
            java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(peerEncoded);
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance(ECDH_ALGORITHM);
            PublicKey peerPublicKey = kf.generatePublic(keySpec);

            KeyAgreement ka = KeyAgreement.getInstance(ECDH_ALGORITHM);
            ka.init(keyPair.getPrivate());
            ka.doPhase(peerPublicKey, true);
            byte[] sharedSecret = ka.generateSecret();

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(sharedSecret);
            sha256.update(HKDF_SALT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] keyBytes = sha256.digest();

            this.aesKey = new SecretKeySpec(keyBytes, "AES");
            log.debug("AES key derived successfully ({} bits)", keyBytes.length * 8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive AES key", e);
        }
    }

    public boolean isEncryptionReady() {
        return aesKey != null;
    }

    public String encrypt(String plaintext) {
        if (aesKey == null) throw new IllegalStateException("AES key not derived yet");
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            java.security.SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedBase64) {
        if (aesKey == null) throw new IllegalStateException("AES key not derived yet");
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            if (combined.length < GCM_IV_LENGTH + 16) {
                throw new RuntimeException("Encrypted data too short");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec);
            byte[] plainBytes = cipher.doFinal(ciphertext);

            return new String(plainBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public byte[] encryptUdp(byte[] data) {
        if (aesKey == null) throw new IllegalStateException("AES key not derived yet");
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            java.security.SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
            byte[] ciphertext = cipher.doFinal(data);

            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("UDP encryption failed", e);
        }
    }

    public byte[] decryptUdp(byte[] data) {
        if (aesKey == null) throw new IllegalStateException("AES key not derived yet");
        try {
            if (data.length < GCM_IV_LENGTH + 16) {
                throw new RuntimeException("UDP encrypted data too short");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);
            byte[] ciphertext = new byte[data.length - GCM_IV_LENGTH];
            System.arraycopy(data, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("UDP decryption failed", e);
        }
    }
}
