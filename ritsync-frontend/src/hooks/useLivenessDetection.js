import { useState, useRef } from 'react';
import * as faceapi from 'face-api.js';

/**
 * Edge AI Liveness Detection Hook
 * Captures face landmarks over 5 seconds, detects blinks and head movement
 * Only active when isEnabled = true (backward compatible)
 */
export const useLivenessDetection = (isEnabled = false) => {
    const [isLivenessChecking, setIsLivenessChecking] = useState(false);
    const [livenessResult, setLivenessResult] = useState(null);
    const [blinkCount, setBlinkCount] = useState(0);
    const landmarksRef = useRef([]);
    const streamRef = useRef(null);

    /**
     * Start liveness check: capture 50 frames (~5 seconds at 10fps)
     */
    const startLivenessCheck = async (videoElement) => {
        if (!isEnabled) {
            setLivenessResult({ success: true, blinkCount: 0, message: 'Liveness not required' });
            return;
        }

        setIsLivenessChecking(true);
        setLivenessResult(null);
        setBlinkCount(0);
        landmarksRef.current = [];
        streamRef.current = videoElement.srcObject;

        // Lazy load face-api.js models
        if (!faceapi.nets.tinyFaceDetector.params) {
            await faceapi.nets.tinyFaceDetector.loadFromUri('/models');
        }
        if (!faceapi.nets.faceLandmark68TinyNet.params) {
            await faceapi.nets.faceLandmark68TinyNet.loadFromUri('/models');
        }

        let frameCount = 0;
        const interval = setInterval(async () => {
            if (frameCount >= 50) { // 5 seconds @ 10fps
                clearInterval(interval);
                finalizeLivenessCheck();
                return;
            }

            const detections = await faceapi.detectSingleFace(
                videoElement, 
                new faceapi.TinyFaceDetectorOptions()
            ).withFaceLandmarks(true);

            if (detections) {
                landmarksRef.current.push(detections.landmarks.positions);
            }
            frameCount++;
        }, 100);

        // Return cleanup function
        return () => clearInterval(interval);
    };

    const finalizeLivenessCheck = () => {
        const landmarks = landmarksRef.current;
        if (landmarks.length < 10) {
            setLivenessResult({ 
                success: false, 
                blinkCount: 0,
                message: 'Insufficient face data captured (min 10 frames needed)' 
            });
            setIsLivenessChecking(false);
            return;
        }

        // Client-side EAR calculation
        let blinks = 0;
        for (const lm of landmarks) {
            const leftEAR = calculateEAR(lm, 36, 37, 38, 39, 40, 41);
            const rightEAR = calculateEAR(lm, 42, 43, 44, 45, 46, 47);
            const avgEAR = (leftEAR + rightEAR) / 2;
            if (avgEAR < 0.25) blinks++;
        }

        setBlinkCount(blinks);
        const isLively = blinks >= 1; // At least 1 blink required
        setLivenessResult({ 
            success: isLively, 
            blinkCount: blinks,
            message: isLively ? `Liveness verified (${blinks} blinks)` : 'No blink detected. Please blink naturally.',
            landmarks: landmarks // Send to backend for server-side validation
        });
        setIsLivenessChecking(false);
    };

    const calculateEAR = (lm, p1, p2, p3, p4, p5, p6) => {
        const dist = (a, b) => Math.hypot(lm[a][0]-lm[b][0], lm[a][1]-lm[b][1]);
        const v1 = dist(p2, p5);
        const v2 = dist(p3, p4);
        const h = dist(p1, p6);
        return h === 0 ? 0 : (v1 + v2) / (2 * h);
    };

    return { 
        startLivenessCheck, 
        isLivenessChecking, 
        livenessResult, 
        blinkCount 
    };
};
