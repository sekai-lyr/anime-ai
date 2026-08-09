package com.example.demo.chat.entity;

/**
消息投影接口。
 * Spring Data JPA投影，用于高效查询消息的部分字段。
 */
public interface MessageProjection {
    String getRole();
    String getContent();
}