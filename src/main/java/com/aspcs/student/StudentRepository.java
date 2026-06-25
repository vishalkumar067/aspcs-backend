package com.aspcs.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {
    Page<Student> findAllByOrderByCreatedAtDesc(Pageable p);
    Page<Student> findByCurrentClassOrderByFullNameAsc(String cls, Pageable p);
    List<Student> findByCurrentClassAndSectionOrderByFullNameAsc(String cls, String section);
    List<Student> findByCurrentClassAndSectionAndStatusOrderByFullNameAsc(String cls, String section, String status);

    @Query("SELECT s FROM Student s WHERE " +
           "LOWER(s.fullName) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(s.admissionNo) LIKE LOWER(CONCAT('%',:q,'%'))")
    Page<Student> search(@Param("q") String q, Pageable p);

    boolean existsByAdmissionNo(String admissionNo);
    long countByStatus(String status);
    long countByCurrentClass(String cls);
}
