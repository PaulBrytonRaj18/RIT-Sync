package com.ritsync.repository;

import com.ritsync.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<LeaveRequest> findByCourseIdOrderByCreatedAtDesc(Long courseId);
    List<LeaveRequest> findByCourseIdAndStatusOrderByCreatedAtDesc(Long courseId, LeaveRequest.LeaveStatus status);
    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveRequest.LeaveStatus status);
}
