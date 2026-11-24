package com.borrowbuddy.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

// encrypts one column's value with AES-GCM before it is written to the database, and
// decrypts it back when Hibernate reads the row, so a column like Profile.contactInfo
// is unreadable to anyone with raw database access (e.g. the Supabase table editor).
//
// needs the FIELD_ENCRYPTION_KEY env var: a base64-encoded 32-byte (256-bit) AES key.
// if that env var is not set, fields are stored as plain text (fine for local dev).
//
// stored values that do not start with the ENC: prefix are treated as legacy plain text
// and returned as-is, so existing rows keep working until they are next saved.
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    private static final String PREFIX = "ENC:";
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final SecretKeySpec KEY = loadKey();

    private static SecretKeySpec loadKey() {
        String base64Key = System.getenv("FIELD_ENCRYPTION_KEY");
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        return new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null || KEY == null) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(cipherText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt field.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || KEY == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            String[] parts = stored.substring(PREFIX.length()).split(":", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] cipherText = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalStateException("Could not decrypt field.", e);
        }
    }
}
