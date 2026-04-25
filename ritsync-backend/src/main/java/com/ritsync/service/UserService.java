package com.ritsync.service;

import com.ritsync.dto.response.UserResponse;
import com.ritsync.entity.User;
import com.ritsync.enums.Role;
import com.ritsync.exception.RitSyncException;
import com.ritsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRole(Role role) {
        List<User> users = userRepository.findByRoleAndIsActiveTrue(role);
        if (users.isEmpty()) return users.stream().map(u -> UserResponse.from(u, false)).toList();

        List<Long> userIds = users.stream().map(User::getId).toList();
        List<Long> enrolledIds = userRepository.findUserIdsWithFaceDescriptor(userIds);
        Set<Long> enrolledSet = new HashSet<>(enrolledIds);

        return users.stream()
                .map(u -> UserResponse.from(u, enrolledSet.contains(u.getId())))
                .toList();
    }

    @Transactional
    public void deactivateUser(Long id) {
        User user = findOrThrow(id);
        user.setIsActive(false);
        userRepository.save(user);
    }
}
