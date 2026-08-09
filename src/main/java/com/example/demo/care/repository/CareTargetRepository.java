package com.example.demo.care.repository;

import com.example.demo.care.model.CareTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
/**
护理目标JPA仓库接口。
 * 提供护理目标（植物/宠物）的CRUD操作。
 */
public interface CareTargetRepository extends JpaRepository<CareTarget, Long> {
    List<CareTarget> findByUserId(String userId);
    Optional<CareTarget> findByIdAndUserId(Long id, String userId);
    List<CareTarget> findByUserIdAndType(String userId, CareTarget.TargetType type);
}
