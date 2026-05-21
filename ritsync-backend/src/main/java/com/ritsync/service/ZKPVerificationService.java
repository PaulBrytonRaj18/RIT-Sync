package com.ritsync.service;

import com.ritsync.entity.FaceDescriptor;
import com.ritsync.repository.FaceDescriptorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZKPVerificationService {

    private final FaceDescriptorRepository faceDescriptorRepository;

    public ChallengePackage initiateChallenge(Long studentId) {
        FaceDescriptor fd = faceDescriptorRepository.findByUserId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Face not enrolled"));

        if (fd.getEncryptedDescriptor() == null) {
            throw new IllegalStateException("User not enrolled with ZKP. Please re-enroll.");
        }

        String challenge = UUID.randomUUID().toString();

        return new ChallengePackage(
                fd.getEncryptedDescriptor(),
                fd.getEncryptionIv(),
                fd.getSalt(),
                challenge
        );
    }

    public boolean verifyResponse(Long studentId, String challenge, String hmac, boolean clientSideResult) {
        log.info("ZKP verification for student {}: challenge={}, result={}",
                studentId, challenge, clientSideResult);

        return clientSideResult;
    }

    public record ChallengePackage(
            String encryptedDescriptor,
            String encryptionIv,
            String salt,
            String challenge
    ) {}

    public static String generateHMAC(String data, byte[] key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC generation failed", e);
        }
    }
}
