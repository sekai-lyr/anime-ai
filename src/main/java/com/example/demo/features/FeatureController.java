package com.example.demo.features;

import com.example.demo.aicare.Result;
import com.example.demo.chat.LlmService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
/**
功能特性REST控制器。
 * 提供功能特性列表和详情的API接口。
 */
public class FeatureController {

    private final FeatureService features;
    private final LlmService llm;

    @Value("${wechat.token:change-me}")
    private String wechatToken;

    public FeatureController(FeatureService features, LlmService llm) {
        this.features = features;
        this.llm = llm;
    }

    @PostMapping("/features/diagnosis")
    public Result<Map<String, Object>> diagnose(@RequestBody Map<String, Object> body, HttpSession session) {
        try {
            return Result.success(features.diagnose(user(session), body));
        } catch (Exception e) {
            return Result.error("病害识别失败：" + e.getMessage());
        }
    }

    @PostMapping("/features/timeline")
    public Result<Map<String, Object>> addTimeline(@RequestBody Map<String, Object> body, HttpSession session) {
        try {
            return Result.success(features.addTimeline(user(session), body));
        } catch (Exception e) {
            return Result.error("时间线保存失败：" + e.getMessage());
        }
    }

    @GetMapping("/features/timeline")
    public Result<List<Map<String, Object>>> timeline(
            @RequestParam(defaultValue = "PLANT") String targetType,
            @RequestParam(required = false) Long targetId,
            HttpSession session) {
        return Result.success(features.timeline(user(session), targetType, targetId));
    }

    @GetMapping("/features/daily-brief")
    public Result<Map<String, String>> dailyBrief(
            @RequestParam(defaultValue = "false") boolean refresh, HttpSession session) {
        try {
            return Result.success(Map.of("content", features.dailyBrief(user(session), refresh)));
        } catch (Exception e) {
            return Result.error("生成简报失败：" + e.getMessage());
        }
    }

    @GetMapping("/features/community/posts")
    public Result<List<Map<String, Object>>> posts() {
        return Result.success(features.posts());
    }

    @PostMapping("/features/community/posts")
    public Result<Map<String, Object>> createPost(@RequestBody Map<String, Object> body, HttpSession session) {
        try {
            return Result.success(features.createPost(user(session), body));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/features/community/posts/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        features.like(id);
        return Result.success(null);
    }

    @GetMapping("/features/community/posts/{id}/comments")
    public Result<List<Map<String, Object>>> comments(@PathVariable Long id) {
        return Result.success(features.comments(id));
    }

    @PostMapping("/features/community/posts/{id}/comments")
    public Result<Void> comment(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        try {
            features.comment(user(session), id, body.get("content"));
            return Result.success(null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/features/inventory")
    public Result<List<Map<String, Object>>> inventory(HttpSession session) {
        return Result.success(features.inventory(user(session)));
    }

    @PostMapping("/features/inventory")
    public Result<Map<String, Long>> saveInventory(@RequestBody Map<String, Object> body, HttpSession session) {
        try {
            return Result.success(Map.of("id", features.saveInventory(user(session), body)));
        } catch (Exception e) {
            return Result.error("保存失败：" + e.getMessage());
        }
    }

    @PostMapping("/features/inventory/{id}/consume")
    public Result<Void> consume(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        features.consume(user(session), id, new BigDecimal(String.valueOf(body.getOrDefault("amount", "1"))));
        return Result.success(null);
    }

    @GetMapping("/features/notifications")
    public Result<List<Map<String, Object>>> notifications(HttpSession session) {
        return Result.success(features.notifications(user(session)));
    }

    @PostMapping("/features/notifications/{id}/read")
    public Result<Void> read(@PathVariable Long id, HttpSession session) {
        features.markRead(user(session), id);
        return Result.success(null);
    }

    @PostMapping("/features/push/subscribe")
    public Result<Void> subscribe(@RequestBody Map<String, Object> body, HttpSession session) {
        features.subscribe(user(session), body);
        return Result.success(null);
    }

    @PostMapping("/wechat/mini/chat")
    public Result<Map<String, String>> miniChat(@RequestBody Map<String, String> body, HttpSession session) {
        try {
            String message = body.getOrDefault("message", "");
            String reply = llm.chatWithMemory("mini_" + user(session), message,
                    "你是微信小程序里的宠物植物护理助手，回答简洁、可靠。");
            return Result.success(Map.of("reply", reply));
        } catch (Exception e) {
            return Result.error("AI回复失败：" + e.getMessage());
        }
    }

    @GetMapping("/wechat/callback")
    public String verifyWechat(
            @RequestParam String signature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestParam String echostr) {
        return validSignature(signature, timestamp, nonce) ? echostr : "invalid signature";
    }

    @PostMapping(value = "/wechat/callback", consumes = MediaType.TEXT_XML_VALUE,
            produces = "application/xml;charset=UTF-8")
    public String receiveWechat(
            @RequestParam String signature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestBody String xml) {
        if (!validSignature(signature, timestamp, nonce)) return "";
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element root = document.getDocumentElement();
            String from = text(root, "FromUserName");
            String to = text(root, "ToUserName");
            String type = text(root, "MsgType");
            String content = "text".equals(type) ? text(root, "Content") : "请发送文字问题，我会提供宠物植物护理建议。";
            String reply = llm.chatWithMemory("wechat_" + from, content,
                    "你是微信公众号里的宠物植物护理助手，回答简洁、可靠。");
            return "<xml><ToUserName><![CDATA[" + safe(from) + "]]></ToUserName>"
                    + "<FromUserName><![CDATA[" + safe(to) + "]]></FromUserName>"
                    + "<CreateTime>" + System.currentTimeMillis() / 1000 + "</CreateTime>"
                    + "<MsgType><![CDATA[text]]></MsgType>"
                    + "<Content><![CDATA[" + safe(reply) + "]]></Content></xml>";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean validSignature(String signature, String timestamp, String nonce) {
        try {
            String[] values = {wechatToken, timestamp, nonce};
            Arrays.sort(values);
            String joined = String.join("", values);
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(joined.getBytes(StandardCharsets.UTF_8)));
            return digest.equalsIgnoreCase(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private String text(Element root, String name) {
        return root.getElementsByTagName(name).item(0).getTextContent();
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("]]>", "");
    }

    private String user(HttpSession session) {
        Object user = session.getAttribute("user");
        return user == null ? "guest" : String.valueOf(user);
    }
}
