package com.example.demo.chat.repository.mysql;

import com.example.demo.chat.entity.UserSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
/**
用户会话JPA仓库接口。
 */
public interface UserSessionRepository extends JpaRepository<UserSessionEntity, String> {
}