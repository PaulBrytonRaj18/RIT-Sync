package com.ritsync.repository;

import com.ritsync.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    Optional<Enrollment> findByStudentIdAndCourseId(Long studentId, Long courseId);
    List<Enrollment> findByCourseIdAndIsActiveTrue(Long courseId);
    boolean existsByStudentIdAndCourseIdAndIsActiveTrue(Long studentId, Long courseId);
}
