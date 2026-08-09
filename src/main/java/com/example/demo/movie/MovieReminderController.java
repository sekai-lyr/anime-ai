package com.example.demo.movie;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reminders")
public class MovieReminderController {

    private final ReminderService reminderService;

    public MovieReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping(value = "/movie.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> movieReminder(
            @RequestParam String title,
            @RequestParam String date,
            @RequestParam(defaultValue = "20:00") String time) {
        LocalDate releaseDate = LocalDate.parse(date);
        String day = releaseDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String compactTime = time.replace(":", "") + "00";
        String calendar = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//AnimeAI//Movie Reminder//CN\r\n"
                + "BEGIN:VEVENT\r\nUID:" + day + "-" + Math.abs(title.hashCode()) + "@animeai\r\n"
                + "DTSTART:" + day + "T" + compactTime + "\r\nDURATION:PT2H\r\n"
                + "SUMMARY:" + escape(title + " 涓婃槧/瑙傚奖鎻愰啋") + "\r\n"
                + "DESCRIPTION:鐢?AnimeAI 鍒涘缓銆傝鍦ㄧ尗鐪兼垨娣樼エ绁ㄧ‘璁ゅ綋鍦板奖闄㈡帓鐗囥€俓r\n"
                + "BEGIN:VALARM\r\nTRIGGER:-P1D\r\nACTION:DISPLAY\r\nDESCRIPTION:鏄庡ぉ璁板緱纭鐢靛奖鎺掔墖\r\nEND:VALARM\r\n"
                + "END:VEVENT\r\nEND:VCALENDAR\r\n";
        String filename = URLEncoder.encode(title + "-鎻愰啋.ics", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .body(calendar);
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createReminder(@RequestBody Map<String, String> request) {
        String conversationId = request.get("conversationId");
        String title = request.get("title");
        String date = request.get("date");
        String time = request.getOrDefault("time", "20:00");

        if (conversationId == null || conversationId.isBlank()
                || title == null || title.isBlank()
                || date == null || date.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing required fields"));
        }

        try {
            LocalDate localDate = LocalDate.parse(date);
            LocalTime localTime = LocalTime.parse(time);
            LocalDateTime remindTime = LocalDateTime.of(localDate, localTime);
            Reminder reminder = reminderService.createReminder(conversationId, title, remindTime);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", reminder.getId(),
                    "message", "提醒已设置：" + title + " - " + date + " " + time
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPending(@RequestParam String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing conversationId"));
        }
        List<String> messages = reminderService.getAndMarkPending(conversationId);
        return ResponseEntity.ok(Map.of("success", true, "messages", messages));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n");
    }
}
