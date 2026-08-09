package com.example.demo.movie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByConversationIdAndSentFalseAndRemindTimeBeforeOrderByRemindTimeAsc(
            String conversationId, LocalDateTime now);

    List<Reminder> findBySentFalseAndRemindTimeBefore(LocalDateTime now);
}
