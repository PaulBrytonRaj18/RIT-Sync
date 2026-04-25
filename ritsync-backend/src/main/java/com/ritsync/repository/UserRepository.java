package com.ritsync.repository;

import com.ritsync.entity.User;
import com.ritsync.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRoleAndIsActiveTrue(Role role);
    List<User> findByDepartmentAndRole(String department, Role role);

    @Query("SELECT u FROM User u WHERE u.role = :role AND u.department = :dept AND u.isActive = true")
    List<User> findActiveByRoleAndDepartment(Role role, String dept);

    @Query("SELECT COUNT(fd) > 0 FROM FaceDescriptor fd WHERE fd.user.id = :userId")
    boolean existsFaceDescriptorByUserId(Long userId);

    @Query("SELECT fd.user.id FROM FaceDescriptor fd WHERE fd.user.id IN :userIds")
    List<Long> findUserIdsWithFaceDescriptor(List<Long> userIds);
}
