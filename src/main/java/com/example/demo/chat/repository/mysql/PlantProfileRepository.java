package com.example.demo.chat.repository.mysql;

import com.example.demo.chat.entity.PlantProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
/**
植物档案JPA仓库接口。
 */
public interface PlantProfileRepository extends JpaRepository<PlantProfile, Long> {
    List<PlantProfile> findByNameContaining(String name);
    List<PlantProfile> findBySpeciesContaining(String species);
}