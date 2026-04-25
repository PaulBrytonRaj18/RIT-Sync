package com.ritsync.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritsync.entity.FaceDescriptor;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.FaceDescriptorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceVerificationService {

    private final FaceDescriptorRepository faceDescriptorRepository;
    private final ObjectMapper             objectMapper;

    @Value("${app.face.threshold:0.55}")
    private double matchThreshold;

    public record VerificationResult(boolean verified, double distance) {}

    public VerificationResult verify(Long userId, List<Double> liveDescriptor) {
        FaceDescriptor enrolled = faceDescriptorRepository.findByUserId(userId)
            .orElseThrow(() -> RitSyncException.badRequest(
                "Face ID not enrolled. Please complete Face ID setup before marking attendance."));

        List<Double> storedDescriptor;
        try {
            storedDescriptor = objectMapper.readValue(
                enrolled.getDescriptor(),
                new TypeReference<List<Double>>() {}
            );
        } catch (Exception e) {
            log.error("Failed to deserialize face descriptor for user {}", userId, e);
            throw RitSyncException.badRequest("Stored face descriptor is corrupted. Please re-enroll.");
        }

        if (storedDescriptor.size() != liveDescriptor.size()) {
            throw RitSyncException.badRequest(
                "Descriptor dimension mismatch: stored=" + storedDescriptor.size()
                + " live=" + liveDescriptor.size());
        }

        double distance = euclideanDistance(storedDescriptor, liveDescriptor);
        log.debug("Face verification for user {}: distance={} threshold={}", userId, distance, matchThreshold);
        return new VerificationResult(distance < matchThreshold, distance);
    }

    private double euclideanDistance(List<Double> a, List<Double> b) {
        double sum = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double diff = a.get(i) - b.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
