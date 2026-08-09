package com.example.demo.chat.repository.mysql;

import com.example.demo.chat.entity.AnimeCharacterProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnimeCharacterProfileRepository extends JpaRepository<AnimeCharacterProfile, Long> {
}
