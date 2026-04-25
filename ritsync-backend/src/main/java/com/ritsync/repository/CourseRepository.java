package com.ritsync.repository;

import com.ritsync.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByInstructorIdAndIsActiveTrue(Long instructorId);
    List<Course> findByDepartmentAndIsActiveTrue(String department);

    @Query("""
        SELECT c FROM Course c
        JOIN Enrollment e ON e.course = c
        WHERE e.student.id = :studentId AND e.isActive = true AND c.isActive = true
    """)
    List<Course> findEnrolledCoursesByStudentId(Long studentId);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.course.id = :courseId AND e.isActive = true")
    int countEnrolledStudents(Long courseId);

    @Query("""
        SELECT c FROM Course c
        JOIN FETCH c.instructor
        WHERE c.id IN :courseIds AND c.isActive = true
    """)
    List<Course> findByIdsWithInstructor(List<Long> courseIds);
}
