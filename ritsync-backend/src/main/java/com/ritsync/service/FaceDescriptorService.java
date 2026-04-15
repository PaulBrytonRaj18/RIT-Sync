package com.ritsync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritsync.entity.FaceDescriptor;
import com.ritsync.entity.User;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.FaceDescriptorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceDescriptorService {

    private final FaceDescriptorRepository faceDescriptorRepository;
    private final UserService              userService;
    private final ObjectMapper             objectMapper;

    @Transactional
    public void enroll(Long userId, List<Double> descriptor) {
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

        FaceDescriptor entity = faceDescriptorRepository.findByUserId(userId)
            .orElseGet(() -> FaceDescriptor.builder().user(user).build());

        entity.setDescriptor(json);
        faceDescriptorRepository.save(entity);
        log.info("Face descriptor enrolled/updated for user {}", userId);
    }

    public boolean isEnrolled(Long userId) {
        return faceDescriptorRepository.existsByUserId(userId);
    }

    public List<Double> getDescriptor(Long userId) {
        return faceDescriptorRepository.findByUserId(userId)
            .map(entity -> {
                try {
                    @SuppressWarnings("unchecked")
                    List<Double> parsed = objectMapper.readValue(entity.getDescriptor(), List.class);
                    return parsed;
                } catch (Exception e) {
                    log.warn("Failed to parse face descriptor for user {}: {}", userId, e.getMessage());
                    return null;
                }
            })
            .orElse(null);
    }
}
