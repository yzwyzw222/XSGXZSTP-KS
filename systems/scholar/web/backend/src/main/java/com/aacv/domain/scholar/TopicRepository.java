package com.aacv.domain.scholar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopicRepository extends JpaRepository<Topic, String> {
    List<Topic> findByIdIn(List<String> ids);
}