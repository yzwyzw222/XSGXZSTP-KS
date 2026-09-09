package com.aacv.domain.scholar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, String> {
    List<Venue> findByIdIn(List<String> ids);
}