package com.aacv.domain.scholar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstitutionRepository extends JpaRepository<Institution, String> {
    List<Institution> findByIdIn(List<String> ids);
}