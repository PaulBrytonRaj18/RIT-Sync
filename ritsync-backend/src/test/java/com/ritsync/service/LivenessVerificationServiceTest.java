package com.ritsync.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LivenessVerificationServiceTest {

    private final LivenessVerificationService service = new LivenessVerificationService();

    /** Synthetic 68-point frame: eye opening height {@code eyeH} (width 30) and nose x-offset. */
    public static List<List<Double>> frame(double eyeH, double noseDx) {
        List<List<Double>> pts = new ArrayList<>();
        for (int i = 0; i < 68; i++) pts.add(List.of(50.0 + i, 200.0));
        eye(pts, 36, 100, eyeH);
        eye(pts, 42, 160, eyeH);
        pts.set(30, List.of(145.0 + noseDx, 140.0));
        return pts;
    }

    private static void eye(List<List<Double>> pts, int start, double x0, double h) {
        double y = 100;
        pts.set(start,     List.of(x0,      y));
        pts.set(start + 1, List.of(x0 + 10, y - h / 2));
        pts.set(start + 2, List.of(x0 + 20, y - h / 2));
        pts.set(start + 3, List.of(x0 + 30, y));
        pts.set(start + 4, List.of(x0 + 20, y + h / 2));
        pts.set(start + 5, List.of(x0 + 10, y + h / 2));
    }

    /** 20 frames, eyes open except a 2-frame blink in the middle, with slight head motion. */
    public static List<List<List<Double>>> blinkingSequence() {
        List<List<List<Double>>> frames = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            double h = (i == 9 || i == 10) ? 3 : 9;   // EAR 0.1 when closed, 0.3 when open
            frames.add(frame(h, i * 0.3));
        }
        return frames;
    }

    @Test
    void naturalBlinkPasses() {
        var r = service.validate(blinkingSequence());
        assertThat(r.success()).isTrue();
        assertThat(r.blinkCount()).isEqualTo(1);
    }

    @Test
    void staticPhotoFails() {
        List<List<List<Double>>> frames = new ArrayList<>();
        for (int i = 0; i < 20; i++) frames.add(frame(9, 0));
        assertThat(service.validate(frames).success()).isFalse();
    }

    @Test
    void movingFaceWithoutBlinkFails() {
        List<List<List<Double>>> frames = new ArrayList<>();
        for (int i = 0; i < 20; i++) frames.add(frame(9, i * 0.5));
        var r = service.validate(frames);
        assertThat(r.success()).isFalse();
        assertThat(r.blinkCount()).isZero();
    }

    @Test
    void eyesClosedThroughoutIsNotABlink() {
        List<List<List<Double>>> frames = new ArrayList<>();
        for (int i = 0; i < 20; i++) frames.add(frame(3, i * 0.5));
        assertThat(service.validate(frames).success()).isFalse();
    }

    @Test
    void tooFewFramesFails() {
        assertThat(service.validate(blinkingSequence().subList(0, 5)).success()).isFalse();
        assertThat(service.validate(null).success()).isFalse();
    }

    @Test
    void malformedFrameFails() {
        var frames = blinkingSequence();
        frames.set(3, frames.get(3).subList(0, 10));
        assertThat(service.validate(frames).success()).isFalse();
    }
}
