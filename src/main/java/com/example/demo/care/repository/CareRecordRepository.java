package com.example.demo.care.repository;

import com.example.demo.care.model.CareRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
/**
护理记录JPA仓库接口。
 * 提供护理记录的CRUD操作和自定义查询方法。
 */
public interface CareRecordRepository extends JpaRepository<CareRecord, Long> {
    List<CareRecord> findByUserId(String userId);
    List<CareRecord> findByUserIdAndTargetId(String userId, Long targetId);
    List<CareRecord> findByUserIdAndRecordType(String userId, CareRecord.RecordType recordType);
    List<CareRecord> findByUserIdAndRecordTypeAndIsCompleted(String userId, CareRecord.RecordType recordType, Boolean isCompleted);
    List<CareRecord> findByReminderTimeBetweenAndIsCompleted(LocalDateTime start, LocalDateTime end, Boolean isCompleted);
}
