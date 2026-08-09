package com.example.demo.chat.repository.mysql;

import com.example.demo.chat.entity.AnimeSeriesProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnimeSeriesProfileRepository extends JpaRepository<AnimeSeriesProfile, Long> {
}
