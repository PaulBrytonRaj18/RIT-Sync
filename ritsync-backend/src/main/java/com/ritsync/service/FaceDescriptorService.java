package com.ritsync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritsync.entity.FaceDescriptor;
import com.ritsync.entity.User;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.FaceDescriptorRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceDescriptorService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final FaceDescriptorRepository faceDescriptorRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Value("${app.encryption.key:}")
    private String encryptionKey;

    private SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            log.warn("ENCRYPTION_KEY not configured. Face descriptors stored unencrypted.");
            keySpec = null;
        } else {
            byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                byte[] paddedKey = new byte[32];
                System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
                keySpec = new SecretKeySpec(paddedKey, "AES");
            } else {
                keySpec = new SecretKeySpec(keyBytes, 0, 32, "AES");
            }
            log.info("Face descriptor encryption enabled.");
        }
    }

    @Transactional
    public void enroll(Long userId, List<Double> descriptor,
                       String encryptedDescriptor, String encryptionIv, String salt) {
        if (descriptor == null || descriptor.size() != 128) {
            throw RitSyncException.badRequest(
                "Face descriptor must be a 128-element float array from face-api.js.");
        }

        User user = userService.findOrThrow(userId);
        String json;
        try {
            json = objectMapper.writeValueAsString(descriptor);
        } catch (Exception e) {
            throw RitSyncException.badRequest("Invalid face descriptor format.");
        }

        String encrypted = encrypt(json);

        FaceDescriptor entity = faceDescriptorRepository.findByUserId(userId)
            .orElseGet(() -> FaceDescriptor.builder().user(user).build());

        entity.setDescriptor(encrypted);
        entity.setEncryptedDescriptor(encryptedDescriptor);
        entity.setEncryptionIv(encryptionIv);
        entity.setSalt(salt);
        faceDescriptorRepository.save(entity);
        log.info("Face descriptor enrolled/updated for user {} with ZKP fields", userId);
    }

    public boolean isEnrolled(Long userId) {
        return faceDescriptorRepository.existsByUserId(userId);
    }

    public List<Double> getDescriptor(Long userId) {
        return faceDescriptorRepository.findByUserId(userId)
            .map(entity -> {
                try {
                    String decrypted = decrypt(entity.getDescriptor());
                    @SuppressWarnings("unchecked")
                    List<Double> parsed = objectMapper.readValue(decrypted, List.class);
                    return parsed;
                } catch (Exception e) {
                    log.warn("Failed to parse face descriptor for user {}: {}", userId, e.getMessage());
                    return null;
                }
            })
            .orElse(null);
    }

    private String encrypt(String plaintext) {
        if (keySpec == null) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw RitSyncException.internal("Failed to encrypt face data");
        }
    }

    private String decrypt(String encrypted) {
        if (keySpec == null) {
            return encrypted;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];

            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw RitSyncException.internal("Failed to decrypt face data");
        }
    }
}
