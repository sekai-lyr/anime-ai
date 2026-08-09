package com.example.demo.chat.repository.sqlite;

import com.example.demo.chat.entity.sqlite.VectorStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
/**
向量存储SQLite仓库接口。
 * 提供向量数据的CRUD和相似度检索操作。
 */
public interface VectorStoreRepository extends JpaRepository<VectorStore, Long> {
    Optional<VectorStore> findByDocumentId(String documentId);
    List<VectorStore> findBySourceId(String sourceId);
    List<VectorStore> findByConversationId(String conversationId);
    void deleteByDocumentId(String documentId);
    void deleteBySourceId(String sourceId);
    void deleteByConversationId(String conversationId);
    long countBySourceId(String sourceId);
}