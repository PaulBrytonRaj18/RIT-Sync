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
        // Biometric data must never be stored in plaintext, and a short key must never be zero-padded.
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                "ENCRYPTION_KEY is not set. Generate one with: openssl rand -base64 32");
        }
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "ENCRYPTION_KEY must be at least 32 bytes (AES-256). Current length: " + keyBytes.length
                + ". Generate one with: openssl rand -base64 32");
        }
        keySpec = new SecretKeySpec(keyBytes, 0, 32, "AES");
        log.info("Face descriptor encryption enabled.");
    }

    @Transactional
    public void enroll(Long userId, List<Double> descriptor,
                       String encryptedDescriptor, String encryptionIv, String salt) {
        if (descriptor == null || descriptor.size() != 128
                || descriptor.stream().anyMatch(d -> d == null || !Double.isFinite(d))) {
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

        // One-time enrollment: re-enrolling would let someone else register their own face on this
        // account and then mark attendance for it. Resets go through an administrator.
        if (faceDescriptorRepository.existsByUserId(userId)) {
            throw RitSyncException.conflict(
                "Face ID is already enrolled. Ask an administrator to reset it if you need to re-enroll.");
        }
        FaceDescriptor entity = FaceDescriptor.builder().user(user).build();

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

    /** Admin-only: removes a user's enrolled face so they can enroll again. */
    @Transactional
    public void resetEnrollment(Long userId) {
        faceDescriptorRepository.findByUserId(userId).ifPresent(fd -> {
            User user = fd.getUser();
            if (user != null) user.setFaceDescriptor(null);
            faceDescriptorRepository.delete(fd);
        });
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
