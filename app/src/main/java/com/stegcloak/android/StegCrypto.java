package com.stegcloak.android;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cryptographic layer, byte-compatible with StegCloak's components/encrypt.js:
 *   keyIv  = PBKDF2-HMAC-SHA512(password, salt(8), 10000 iters, 48 bytes)
 *   iv     = keyIv[0..16)   key = keyIv[16..48)
 *   cipher = AES-256-CTR(key, iv)
 *   integrity mode additionally stores HMAC-SHA256(key, plaintext)
 *   payload = salt || [hmac] || ciphertext
 */
final class StegCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();

    private StegCrypto() {}

    /** Manual PBKDF2-HMAC-SHA512 (provider-independent, works on every Android API level). */
    static byte[] pbkdf2Sha512(byte[] password, byte[] salt, int iterations, int dkLen) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            // Node.js accepts an empty password; JCA rejects an empty SecretKeySpec.
            // HMAC pads short keys with zero bytes to the 128-byte block, so an all-zero
            // key block is exactly equivalent to an empty key.
            if (password.length == 0) password = new byte[128];
            mac.init(new SecretKeySpec(password, "HmacSHA512"));
            final int hLen = 64;
            byte[] block = Arrays.copyOf(salt, salt.length + 4);
            byte[] dk = new byte[dkLen];
            int blockCount = (dkLen + hLen - 1) / hLen;
            for (int b = 1; b <= blockCount; b++) {
                block[salt.length] = 0;
                block[salt.length + 1] = 0;
                block[salt.length + 2] = 0;
                block[salt.length + 3] = (byte) b;
                byte[] u = mac.doFinal(block);
                byte[] t = u.clone();
                for (int i = 1; i < iterations; i++) {
                    u = mac.doFinal(u);
                    for (int k = 0; k < hLen; k++) t[k] ^= u[k];
                }
                System.arraycopy(t, 0, dk, (b - 1) * hLen, Math.min(hLen, dkLen - (b - 1) * hLen));
            }
            return dk;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] aesCtr(byte[] key, byte[] iv, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] encrypt(byte[] secret, byte[] password, boolean integrity) {
        byte[] salt = new byte[8];
        RANDOM.nextBytes(salt);
        byte[] keyIv = pbkdf2Sha512(password, salt, 10000, 48);
        byte[] iv = Arrays.copyOfRange(keyIv, 0, 16);
        byte[] key = Arrays.copyOfRange(keyIv, 16, 48);
        byte[] ciphertext = aesCtr(key, iv, secret);
        byte[] out;
        if (integrity) {
            byte[] hmac = hmacSha256(key, secret);
            out = new byte[8 + 32 + ciphertext.length];
            System.arraycopy(salt, 0, out, 0, 8);
            System.arraycopy(hmac, 0, out, 8, 32);
            System.arraycopy(ciphertext, 0, out, 40, ciphertext.length);
        } else {
            out = new byte[8 + ciphertext.length];
            System.arraycopy(salt, 0, out, 0, 8);
            System.arraycopy(ciphertext, 0, out, 8, ciphertext.length);
        }
        return out;
    }

    static byte[] decrypt(byte[] data, byte[] password, boolean integrity) throws Exception {
        if (data.length < 8) throw new Exception("Invalid concealed payload");
        byte[] salt = Arrays.copyOfRange(data, 0, 8);
        byte[] hmacData = null;
        byte[] secret;
        if (integrity) {
            if (data.length < 40) throw new Exception("Invalid concealed payload");
            hmacData = Arrays.copyOfRange(data, 8, 40);
            secret = Arrays.copyOfRange(data, 40, data.length);
        } else {
            secret = Arrays.copyOfRange(data, 8, data.length);
        }
        byte[] keyIv = pbkdf2Sha512(password, salt, 10000, 48);
        byte[] iv = Arrays.copyOfRange(keyIv, 0, 16);
        byte[] key = Arrays.copyOfRange(keyIv, 16, 48);
        byte[] decrypted = aesCtr(key, iv, secret);
        if (integrity) {
            byte[] expected = hmacSha256(key, decrypted);
            if (!MessageDigest.isEqual(hmacData, expected)) {
                throw new Exception("Wrong password or corrupted payload (HMAC integrity failure)");
            }
        }
        return decrypted;
    }
}
