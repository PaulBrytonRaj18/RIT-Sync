package com.ritsync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
public class LivenessVerificationService {

    private static final double EAR_THRESHOLD = 0.25;
    private static final int MIN_BLINKS = 1;
    private static final double HEAD_MOVEMENT_THRESHOLD = 0.02;

    public LivenessResult validate(List<float[][]> landmarksList) {
        if (landmarksList == null || landmarksList.size() < 10) {
            return new LivenessResult(false, 0, "Insufficient frames (min 10 required)");
        }

        int blinkCount = 0;
        float[] prevNose = null;
        boolean headMoved = false;

        for (float[][] lm : landmarksList) {
            double leftEAR = calculateEAR(lm, 36, 37, 38, 39, 40, 41);
            double rightEAR = calculateEAR(lm, 42, 43, 44, 45, 46, 47);
            if ((leftEAR + rightEAR) / 2 < EAR_THRESHOLD) {
                blinkCount++;
            }

            if (lm.length > 30) {
                float[] nose = lm[30];
                if (prevNose != null) {
                    double deltaX = Math.abs(nose[0] - prevNose[0]);
                    double deltaY = Math.abs(nose[1] - prevNose[1]);
                    if (deltaX > HEAD_MOVEMENT_THRESHOLD || deltaY > HEAD_MOVEMENT_THRESHOLD) {
                        headMoved = true;
                    }
                }
                prevNose = nose;
            }
        }

        boolean success = blinkCount >= MIN_BLINKS && headMoved;
        String message = success ? 
            "Verified (" + blinkCount + " blinks)" : 
            "Failed (blinks: " + blinkCount + ")";
        return new LivenessResult(success, blinkCount, message);
    }

    private double calculateEAR(float[][] lm, int p1, int p2, int p3, int p4, int p5, int p6) {
        double v1 = dist(lm[p2], lm[p5]);
        double v2 = dist(lm[p3], lm[p4]);
        double h = dist(lm[p1], lm[p6]);
        return h == 0 ? 0 : (v1 + v2) / (2 * h);
    }

    private double dist(float[] a, float[] b) {
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2));
    }

    public static class LivenessResult {
        private final boolean success;
        private final int blinkCount;
        private final String message;

        public LivenessResult(boolean s, int b, String m) {
            success = s;
            blinkCount = b;
            message = m;
        }

        public boolean isSuccess() { return success; }
        public int getBlinkCount() { return blinkCount; }
        public String getMessage() { return message; }
    }
}
