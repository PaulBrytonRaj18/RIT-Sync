package com.ritsync.service;

import com.ritsync.exception.RitSyncException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Compares a live face descriptor with the enrolled (encrypted-at-rest) template.
 * <p>
 * Besides the usual "too far → different person" threshold, a descriptor that is
 * <em>almost identical</em> to the template is rejected as a probable replay: two genuine
 * captures from a camera never produce the same embedding, but a copied template does.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaceVerificationService {

    private final FaceDescriptorService faceDescriptorService;

    @Value("${app.face.threshold:0.55}")
    private double matchThreshold;

    @Value("${app.face.replay-threshold:0.02}")
    private double replayThreshold;

    public record VerificationResult(boolean verified, double distance, boolean suspectedReplay) {}

    public VerificationResult verify(Long userId, List<Double> liveDescriptor) {
        List<Double> stored = faceDescriptorService.getDescriptor(userId);
        if (stored == null) {
            throw RitSyncException.badRequest(
                "Face ID not enrolled. Please complete Face ID setup before marking attendance.");
        }
        if (liveDescriptor == null || liveDescriptor.size() != stored.size()
                || liveDescriptor.stream().anyMatch(d -> d == null || !Double.isFinite(d))) {
            throw RitSyncException.badRequest("Invalid face descriptor.");
        }

        double distance = euclideanDistance(stored, liveDescriptor);
        boolean replay = distance < replayThreshold;
        boolean verified = !replay && distance < matchThreshold;
        if (replay) {
            log.warn("Suspected face descriptor replay for user {} (distance={})", userId, distance);
        }
        return new VerificationResult(verified, distance, replay);
    }

    static double euclideanDistance(List<Double> a, List<Double> b) {
        double sum = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double diff = a.get(i) - b.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
