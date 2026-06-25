package com.aspcs.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// subjects already existed (seeded in V2) but had no API at all until this module.
public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    List<Subject> findAllByOrderByNameAsc();
    boolean existsByCode(String code);
}
