package com.ritsync.repository;

import com.ritsync.entity.FaceDescriptor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FaceDescriptorRepository extends JpaRepository<FaceDescriptor, Long> {
    Optional<FaceDescriptor> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}
