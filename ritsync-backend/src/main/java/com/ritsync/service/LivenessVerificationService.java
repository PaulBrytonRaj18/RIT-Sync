package com.ritsync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Server-side liveness analysis over a sequence of 68-point facial landmark frames
 * (face-api.js / dlib ordering).
 * <p>
 * A blink is counted as an eye-aspect-ratio (EAR) transition open → closed → open,
 * so a photo or frozen frame cannot pass. EAR is scale invariant, and motion is measured
 * relative to inter-ocular distance, so results don't depend on camera resolution.
 * <p>
 * <b>Threat model:</b> landmarks are computed on the client, so a determined attacker who
 * scripts the API can still fabricate a plausible sequence. This check raises the cost of
 * photo/screen spoofing from a browser; it is not cryptographic proof of presence.
 */
@Slf4j
@Service
public class LivenessVerificationService {

    static final int    MIN_FRAMES          = 10;
    static final int    LANDMARK_COUNT      = 68;
    static final double EAR_CLOSED          = 0.21;
    static final double EAR_OPEN            = 0.25;
    static final int    MIN_BLINKS          = 1;
    /** Below this normalised jitter the sequence is treated as a frozen/synthetic image. */
    static final double MIN_NATURAL_MOTION  = 0.002;

    public record LivenessResult(boolean success, int blinkCount, String message) {}

    public LivenessResult validate(List<List<List<Double>>> frames) {
        if (frames == null || frames.size() < MIN_FRAMES) {
            return new LivenessResult(false, 0, "Insufficient frames (min " + MIN_FRAMES + " required)");
        }
        double[][][] lm = new double[frames.size()][][];
        for (int i = 0; i < frames.size(); i++) {
            lm[i] = toPoints(frames.get(i));
            if (lm[i] == null) {
                return new LivenessResult(false, 0, "Malformed landmark frame");
            }
        }

        int blinks = 0;
        boolean eyesClosed = false;
        double maxMotion = 0;
        double[] firstNose = null;

        for (double[][] f : lm) {
            double ear = (ear(f, 36, 37, 38, 39, 40, 41) + ear(f, 42, 43, 44, 45, 46, 47)) / 2.0;
            if (!eyesClosed && ear < EAR_CLOSED) {
                eyesClosed = true;
            } else if (eyesClosed && ear > EAR_OPEN) {
                eyesClosed = false;
                blinks++;
            }

            double interOcular = dist(f[36], f[45]);
            if (interOcular <= 0) {
                return new LivenessResult(false, blinks, "Malformed landmark frame");
            }
            if (firstNose == null) {
                firstNose = f[30];
            } else {
                maxMotion = Math.max(maxMotion, dist(f[30], firstNose) / interOcular);
            }
        }

        if (maxMotion < MIN_NATURAL_MOTION) {
            return new LivenessResult(false, blinks, "No natural movement detected");
        }
        boolean ok = blinks >= MIN_BLINKS;
        return new LivenessResult(ok, blinks,
            ok ? "Liveness verified (" + blinks + " blink" + (blinks == 1 ? "" : "s") + ")"
               : "No blink detected");
    }

    private static double[][] toPoints(List<List<Double>> frame) {
        if (frame == null || frame.size() != LANDMARK_COUNT) return null;
        double[][] pts = new double[LANDMARK_COUNT][];
        for (int i = 0; i < LANDMARK_COUNT; i++) {
            List<Double> p = frame.get(i);
            if (p == null || p.size() != 2 || p.get(0) == null || p.get(1) == null
                || !Double.isFinite(p.get(0)) || !Double.isFinite(p.get(1))) {
                return null;
            }
            pts[i] = new double[]{p.get(0), p.get(1)};
        }
        return pts;
    }

    private static double ear(double[][] f, int p1, int p2, int p3, int p4, int p5, int p6) {
        double vertical1 = dist(f[p2], f[p6]);
        double vertical2 = dist(f[p3], f[p5]);
        double horizontal = dist(f[p1], f[p4]);
        return horizontal == 0 ? 0 : (vertical1 + vertical2) / (2.0 * horizontal);
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }
}
