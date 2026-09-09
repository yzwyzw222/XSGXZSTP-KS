package com.aacv.domain.scholar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, String> {
    List<Author> findByIdIn(List<String> ids);

    // 按姓名模糊检索学者
    List<Author> findByNameContaining(String name);
}