package com.example.demo.features;

import com.example.demo.chat.LlmService;
import com.example.demo.vision.VisionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
/**
功能特性业务服务。
 * 管理功能特性的开关、配置和查询。
 */
public class FeatureService {

    private final JdbcTemplate jdbc;
    private final VisionService vision;
    private final LlmService llm;

    public FeatureService(JdbcTemplate jdbc, VisionService vision, LlmService llm) {
        this.jdbc = jdbc;
        this.vision = vision;
        this.llm = llm;
    }

    public Map<String, Object> diagnose(String user, Map<String, Object> body) throws IOException {
        String type = string(body.get("targetType"), "PLANT").toUpperCase();
        Long targetId = number(body.get("targetId"));
        String question = string(body.get("question"), "请识别可能的病害并给出处置建议。");
        StoredImage image = storeImage(string(body.get("image"), null), "diagnosis");
        String prompt = "PLANT".equals(type)
                ? "你是植物病虫害专家。识别叶片或植株的病虫害（如白粉病、黑斑病、蚜虫等），说明判断依据、严重程度、隔离措施、治疗步骤和何时需要专业帮助。用户问题：" + question
                : "你是宠物皮肤健康辅助识别助手。识别可能的猫癣、湿疹、过敏、寄生虫或伤口，说明判断依据、风险、居家护理和必须就医的警示。不得替代兽医确诊。用户问题：" + question;
        String result = vision.analyzeImageWithCustomPrompt(image.bytes(), prompt);
        jdbc.update("""
            INSERT INTO disease_diagnosis(user_id,target_type,target_id,image_url,question,diagnosis,created_at)
            VALUES(?,?,?,?,?,?,?)
            """, user, type, targetId, image.url(), question, result, LocalDateTime.now());
        addTimeline(user, type, targetId, image.url(), question, result);
        return Map.of("diagnosis", result, "imageUrl", image.url());
    }

    public Map<String, Object> addTimeline(String user, Map<String, Object> body) throws IOException {
        String type = string(body.get("targetType"), "PLANT").toUpperCase();
        Long targetId = number(body.get("targetId"));
        String note = string(body.get("note"), "");
        StoredImage image = storeImage(string(body.get("image"), null), "timeline");
        String annotation = vision.analyzeImageWithCustomPrompt(image.bytes(),
                "为宠物或植物成长相册生成简短客观标注。指出可见变化、健康状态和可能的成长节点，不确定时明确说明。用户备注：" + note);
        addTimeline(user, type, targetId, image.url(), note, annotation);
        return Map.of("annotation", annotation, "imageUrl", image.url());
    }

    private void addTimeline(String user, String type, Long targetId, String imageUrl, String note, String annotation) {
        jdbc.update("""
            INSERT INTO growth_timeline(user_id,target_type,target_id,image_url,note,ai_annotation,captured_at)
            VALUES(?,?,?,?,?,?,?)
            """, user, type, targetId, imageUrl, note, annotation, LocalDateTime.now());
    }

    public List<Map<String, Object>> timeline(String user, String type, Long targetId) {
        if (targetId == null) {
            return jdbc.queryForList("""
                SELECT * FROM growth_timeline WHERE user_id=? AND target_type=?
                ORDER BY captured_at DESC LIMIT 100
                """, user, type.toUpperCase());
        }
        return jdbc.queryForList("""
            SELECT * FROM growth_timeline WHERE user_id=? AND target_type=? AND target_id=?
            ORDER BY captured_at DESC LIMIT 100
            """, user, type.toUpperCase(), targetId);
    }

    public String dailyBrief(String user, boolean refresh) throws IOException {
        if (!refresh) {
            List<Map<String, Object>> cached = jdbc.queryForList(
                    "SELECT content FROM daily_brief WHERE user_id=? AND brief_date=?", user, LocalDate.now());
            if (!cached.isEmpty()) return String.valueOf(cached.get(0).get("content"));
        }
        List<Map<String, Object>> reminders = safeQuery("""
            SELECT content,due_at,status FROM care_reminder
            WHERE user_id=? AND status IN ('PENDING','SENT') ORDER BY due_at LIMIT 8
            """, user);
        List<Map<String, Object>> records = safeQuery("""
            SELECT content,created_at FROM care_records
            WHERE userId=? ORDER BY created_at DESC LIMIT 5
            """, user);
        List<Map<String, Object>> stocks = jdbc.queryForList("""
            SELECT name,quantity,unit,daily_usage,reorder_threshold FROM supply_inventory
            WHERE user_id=? ORDER BY updated_at DESC
            """, user);
        String prompt = "生成今日宠物植物护理简报，包含：今日待办、天气影响提示（无法获取地点时给通用提示）、历史护理回顾、一条护理知识、库存预警。简洁分段。\n"
                + "提醒：" + reminders + "\n历史：" + records + "\n库存：" + stocks;
        String content = llm.chat(prompt, "你是每日宠物植物护理播报助手。不要编造具体天气数据。");
        jdbc.update("""
            INSERT INTO daily_brief(user_id,brief_date,content,created_at) VALUES(?,?,?,?)
            ON DUPLICATE KEY UPDATE content=VALUES(content),created_at=VALUES(created_at)
            """, user, LocalDate.now(), content, LocalDateTime.now());
        notify(user, "今日护理简报", content, "DAILY_BRIEF", "/features");
        return content;
    }

    public Map<String, Object> createPost(String user, Map<String, Object> body) {
        String title = string(body.get("title"), "").trim();
        String content = string(body.get("content"), "").trim();
        if (title.isEmpty() || content.isEmpty()) throw new IllegalArgumentException("标题和内容不能为空");
        String tags = autoTags(title + " " + content);
        jdbc.update("""
            INSERT INTO community_post(user_id,title,content,tags,image_url,created_at)
            VALUES(?,?,?,?,?,?)
            """, user, title, content, tags, string(body.get("imageUrl"), null), LocalDateTime.now());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return Map.of("id", id, "tags", tags);
    }

    public List<Map<String, Object>> posts() {
        return jdbc.queryForList("""
            SELECT p.*, (SELECT COUNT(*) FROM community_comment c WHERE c.post_id=p.id) comment_count
            FROM community_post p ORDER BY created_at DESC LIMIT 100
            """);
    }

    public void like(Long id) {
        jdbc.update("UPDATE community_post SET like_count=like_count+1 WHERE id=?", id);
    }

    public void comment(String user, Long id, String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("评论不能为空");
        jdbc.update("INSERT INTO community_comment(post_id,user_id,content,created_at) VALUES(?,?,?,?)",
                id, user, content.trim(), LocalDateTime.now());
    }

    public List<Map<String, Object>> comments(Long id) {
        return jdbc.queryForList("SELECT * FROM community_comment WHERE post_id=? ORDER BY created_at", id);
    }

    public Long saveInventory(String user, Map<String, Object> body) {
        Long id = number(body.get("id"));
        Object[] values = {
                string(body.get("name"), "未命名用品"), string(body.get("category"), ""),
                decimal(body.get("quantity")), string(body.get("unit"), ""),
                decimal(body.get("dailyUsage")), body.get("openedAt"),
                decimal(body.get("reorderThreshold")), number(body.get("productId")),
                LocalDateTime.now()
        };
        if (id == null) {
            jdbc.update("""
                INSERT INTO supply_inventory(user_id,name,category,quantity,unit,daily_usage,opened_at,reorder_threshold,product_id,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, user, values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8]);
            return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }
        jdbc.update("""
            UPDATE supply_inventory SET name=?,category=?,quantity=?,unit=?,daily_usage=?,opened_at=?,
              reorder_threshold=?,product_id=?,updated_at=? WHERE id=? AND user_id=?
            """, values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8], id, user);
        return id;
    }

    public List<Map<String, Object>> inventory(String user) {
        return jdbc.queryForList("""
            SELECT i.*, CASE WHEN daily_usage>0 THEN ROUND(quantity/daily_usage,1) ELSE NULL END estimated_days_left,
              CASE WHEN quantity<=reorder_threshold THEN TRUE ELSE FALSE END needs_reorder
            FROM supply_inventory i WHERE user_id=? ORDER BY needs_reorder DESC,updated_at DESC
            """, user);
    }

    public void consume(String user, Long id, BigDecimal amount) {
        jdbc.update("""
            UPDATE supply_inventory SET quantity=GREATEST(0,quantity-?),updated_at=NOW()
            WHERE id=? AND user_id=?
            """, amount, id, user);
    }

    public void subscribe(String user, Map<String, Object> body) {
        jdbc.update("""
            INSERT INTO push_subscription(user_id,device_name,endpoint,p256dh,auth_secret,created_at)
            VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),device_name=VALUES(device_name)
            """, user, string(body.get("deviceName"), "浏览器"), string(body.get("endpoint"), ""),
                string(body.get("p256dh"), null), string(body.get("auth"), null), LocalDateTime.now());
    }

    public List<Map<String, Object>> notifications(String user) {
        return jdbc.queryForList("""
            SELECT * FROM user_notification WHERE user_id=? ORDER BY created_at DESC LIMIT 100
            """, user);
    }

    public void markRead(String user, Long id) {
        jdbc.update("UPDATE user_notification SET is_read=TRUE WHERE id=? AND user_id=?", id, user);
    }

    public void notify(String user, String title, String content, String type, String link) {
        jdbc.update("""
            INSERT INTO user_notification(user_id,title,content,notification_type,link_url,created_at)
            VALUES(?,?,?,?,?,?)
            """, user, title, content, type, link, LocalDateTime.now());
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Shanghai")
    public void generateMorningBriefs() {
        for (Map<String, Object> row : jdbc.queryForList("SELECT user_name FROM users")) {
            try {
                dailyBrief(String.valueOf(row.get("user_name")), true);
            } catch (Exception ignored) {
            }
        }
    }

    @Scheduled(cron = "0 0 */2 * * *", zone = "Asia/Shanghai")
    public void checkInventory() {
        for (Map<String, Object> row : jdbc.queryForList("""
            SELECT user_id,name,quantity,unit,daily_usage,reorder_threshold FROM supply_inventory
            WHERE quantity<=reorder_threshold OR (daily_usage>0 AND quantity/daily_usage<=3)
            """)) {
            String user = String.valueOf(row.get("user_id"));
            String title = "用品补货提醒：" + row.get("name");
            List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id FROM user_notification WHERE user_id=? AND title=? AND created_at>DATE_SUB(NOW(),INTERVAL 1 DAY)
                """, user, title);
            if (existing.isEmpty()) {
                notify(user, title, "当前余量 " + row.get("quantity") + row.get("unit") + "，建议尽快补货。",
                        "REORDER", "/shop");
            }
        }
    }

    private List<Map<String, Object>> safeQuery(String sql, Object... args) {
        try {
            return jdbc.queryForList(sql, args);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String autoTags(String text) {
        StringBuilder tags = new StringBuilder();
        addTag(tags, text, "猫", "#猫咪");
        addTag(tags, text, "狗", "#狗狗");
        addTag(tags, text, "呕吐", "#呕吐");
        addTag(tags, text, "皮肤", "#皮肤");
        addTag(tags, text, "多肉", "#多肉");
        addTag(tags, text, "徒长", "#徒长");
        addTag(tags, text, "黄叶", "#黄叶");
        addTag(tags, text, "病虫", "#病虫害");
        return tags.length() == 0 ? "#护理经验" : tags.toString().trim();
    }

    private void addTag(StringBuilder tags, String text, String keyword, String tag) {
        if (text.contains(keyword)) tags.append(tag).append(' ');
    }

    private StoredImage storeImage(String base64, String folder) throws IOException {
        if (base64 == null || base64.isBlank()) throw new IllegalArgumentException("请选择图片");
        if (base64.contains(",")) base64 = base64.substring(base64.indexOf(',') + 1);
        byte[] bytes = Base64.getDecoder().decode(base64);
        if (bytes.length > 10 * 1024 * 1024) throw new IllegalArgumentException("图片不能超过10MB");
        Path directory = Path.of("uploads", folder);
        Files.createDirectories(directory);
        String name = UUID.randomUUID() + ".jpg";
        Files.write(directory.resolve(name), bytes);
        return new StoredImage(bytes, "/uploads/" + folder + "/" + name);
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private Long number(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return value instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(value));
    }

    private BigDecimal decimal(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(value));
    }

    private record StoredImage(byte[] bytes, String url) {
    }
}
