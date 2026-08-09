package com.example.demo.movie;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ReminderService {

    private final ReminderRepository reminderRepository;

    public ReminderService(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    @Transactional
    public Reminder createReminder(String conversationId, String title, LocalDateTime remindTime) {
        Reminder saved = reminderRepository.save(new Reminder(conversationId, title, remindTime));
        log.info("Reminder created: id={}, conversationId={}, title={}, time={}",
                saved.getId(), conversationId, title, remindTime);
        return saved;
    }

    @Transactional
    public List<String> getAndMarkPending(String conversationId) {
        List<Reminder> due = reminderRepository
                .findByConversationIdAndSentFalseAndRemindTimeBeforeOrderByRemindTimeAsc(
                        conversationId, LocalDateTime.now());
        List<String> messages = new ArrayList<>();
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
        for (Reminder reminder : due) {
            reminder.setSent(true);
            messages.add("🔔 **提醒时间到！** " + reminder.getTitle()
                    + "（设定时间：" + reminder.getRemindTime().format(timeFormat) + "）");
        }
        if (!due.isEmpty()) reminderRepository.saveAll(due);
        return messages;
    }

    @Scheduled(fixedRate = 5000)
    public void checkReminders() {
        long dueCount = reminderRepository.findBySentFalseAndRemindTimeBefore(LocalDateTime.now()).size();
        if (dueCount > 0) log.debug("{} reminder(s) waiting for chat delivery", dueCount);
    }
}
