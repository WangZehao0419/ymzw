package com.ruoyi.alert.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stepone AI 语音外呼客户端(REST 直连,不经过 OpenClaw/Skill 脚本)
 * <p>
 * 平台背景(2026-09 取证): API 基址 open-skill-api.steponeai.com,认证头 X-API-Key,
 * 协议头 X-Skill-Version: 1.0.0。按分钟计费,新用户有体验额度。
 * 为什么选它替代阿里云: 阿里云语音需企业实名资质与 TTS 模板审核,学生团队走不通;
 * Stepone 注册即用,作为告警语音渠道的真实实现。
 * </p>
 * <p>
 * 两条铁律(违反=生产事故):
 * 1. 失败绝不重试——平台 Idempotency-Key 服务端不承诺去重,盲目重试=重复呼叫真人;
 *    超时/异常只向上抛,由 VoiceCallService 记日志留存 call_id 供人工排查。
 * 2. 平台的"AI 身份告知/敏感信息保护/任务结束即挂断"规则是「客户端脚本」附加的,
 *    直连 REST 必须在 user_requirement 里自带等价约束,否则通话不合规。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
public class SteponeVoiceClient {

    /** API Key(Nacos 下发,不入 Git;未配置时 isConfigured()=false 走模拟分支) */
    @Value("${alert.notify.voice.stepone.api-key:}")
    private String apiKey;

    /** API 基址:默认公网;平台仅允许 HTTPS 自定义地址,此处不做本地 mock 改写,模拟由 VoiceCallService 负责 */
    @Value("${alert.notify.voice.stepone.api-base:https://open-skill-api.steponeai.com}")
    private String apiBase;

    /** 响应是固定小 JSON,正则取 call_id 足够;与 VoiceCallService 既有正则提取风格一致 */
    private static final Pattern CALL_ID_PATTERN = Pattern.compile("\"call_id\"\\s*:\\s*\"([^\"]*)\"");

    // 独立构造而非注入:5s 连接/读超时是旁路动作的兜底,防止外呼无响应挂死 @Async 线程
    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    /** API Key 已配置才允许真实外呼;未配置时 VoiceCallService 降级走模拟分支 */
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    /**
     * 发起单向播报外呼(异步受理模型,不等通话结束)
     *
     * @param phone        已规范化的 11 位被叫号码
     * @param announcement 播报文本(与邮件/前端 TTS 文案口径一致)
     * @return call_id(平台通话句柄,失败重试禁令下仅作日志留存与人工查询用)
     */
    public String callAnnouncement(String phone, String announcement) {
        Map<String, Object> body = new HashMap<>();
        body.put("phones", phone);
        body.put("user_requirement", buildAnnouncementTask(announcement));
        String resp = post("/api/v1/callinfo/initiate_call", body);
        String callId = extractField(resp, CALL_ID_PATTERN);
        log.info("[Voice] Stepone 外呼已受理: callId={}, 被叫={}", callId, phone);
        return callId;
    }

    /**
     * 查询通话状态/转写/费用(排障用;超时后先查此处,绝不直接重复拨号)
     */
    public String queryCall(String callId) {
        Map<String, Object> body = new HashMap<>();
        body.put("call_id", callId);
        return post("/api/v1/callinfo/search_callinfo", body);
    }

    /**
     * 查询账号余额(演示前额度自查口径)
     */
    public String balance() {
        ResponseEntity<String> resp = restTemplate.exchange(
                apiBase + "/api/v1/callinfo/balance", HttpMethod.GET,
                new HttpEntity<>(baseHeaders()), String.class);
        return resp.getBody();
    }

    /**
     * 单向播报任务模板
     * <p>
     * 平台是对话式外呼引擎,"单向播报"是靠任务提示词约束出来的行为:
     * 开场 AI 身份告知(合规)→ 只播报一遍 → 不理会对方任何回应 → 播完即挂。
     * 对方强行插话时 AI 可能得到一句简短应答,属已知边界,验收判据见 checklist C4。
     * </p>
     */
    private String buildAnnouncementTask(String announcement) {
        return "你是设备告警语音通知员。开场先用一句话说明你是 AI 语音助手；"
                + "然后完整播报以下告警内容，只播报一遍；不要向对方提问，不要与对方交流或回答任何问题，"
                + "对方任何回应都不需要理会；播报完毕后说\"再见\"并立即结束通话。"
                + "告警内容：" + announcement;
    }

    private String post(String path, Map<String, Object> body) {
        HttpHeaders headers = baseHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    apiBase + path, new HttpEntity<>(body, headers), String.class);
            return resp.getBody();
        } catch (RestClientException e) {
            // 包一层带路径的异常信息:外层 VoiceCallService 只记 message,丢失路径会排障困难
            throw new IllegalStateException("Stepone API 调用失败: " + path + ", " + e.getMessage(), e);
        }
    }

    private HttpHeaders baseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        // 协议版本头:服务端按此识别兼容协议,缺失可能被拒
        headers.set("X-Skill-Version", "1.0.0");
        headers.set("X-Client-Platform", "ymzw-alert");
        return headers;
    }

    /** 从响应体提取指定 JSON 字段值(简单正则,找不到返回 null) */
    private static String extractField(String body, Pattern pattern) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }
}
