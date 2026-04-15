package com.ritsync.service;

import com.ritsync.dto.response.UserResponse;
import com.ritsync.entity.User;
import com.ritsync.enums.Role;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getById(Long id) {
        User user = findOrThrow(id);
        boolean faceEnrolled = userRepository.existsFaceDescriptorByUserId(id);
        return UserResponse.from(user, faceEnrolled);
    }

    public User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> RitSyncException.notFound("User not found: " + id));
    }

    public List<UserResponse> getUsersByRole(Role role) {
        return userRepository.findByRoleAndIsActiveTrue(role).stream()
                .map(u -> UserResponse.from(u, userRepository.existsFaceDescriptorByUserId(u.getId())))
                .toList();
    }

    @Transactional
    public void deactivateUser(Long id) {
        User user = findOrThrow(id);
        user.setIsActive(false);
        userRepository.save(user);
    }
}
