package com.ruoyi.alert.service;

import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.system.api.domain.SysUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 告警电话外呼服务(阿里云语音 SingleCallByTts,文本转语音模板)
 * <p>
 * 外呼是旁路触达渠道:任何失败只记日志,绝不向调用方抛异常,
 * 保证不影响告警落库/流推送/邮件等其他链路。
 * 阿里云密钥未配置(占位)时降级为模拟外呼日志,本地联调零成本零骚扰。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
public class VoiceCallService {

    @Value("${alert.notify.voice.access-key-id:}")
    private String accessKeyId;

    @Value("${alert.notify.voice.access-key-secret:}")
    private String accessKeySecret;

    /** 语音通知 TTS 模板 ID(阿里云控制台申请,单文本变量 tts) */
    @Value("${alert.notify.voice.template-code:}")
    private String templateCode;

    /** 被叫显号(阿里云控制台申请的号码,空则用账号默认显号) */
    @Value("${alert.notify.voice.called-show-number:}")
    private String calledShowNumber;

    /** dyvmsapi(语音服务)旧版 RPC 接入点 */
    @Value("${alert.notify.voice.endpoint:dyvmsapi.aliyuncs.com}")
    private String endpoint;

    /** V1 签名公共参数 Timestamp 格式:UTC ISO8601 */
    private static final DateTimeFormatter UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    /** 响应是固定小 JSON,正则取 Code/CallId 足够;解析不出仅影响日志完整性,不影响链路 */
    private static final Pattern CODE_PATTERN = Pattern.compile("\"Code\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern CALL_ID_PATTERN = Pattern.compile("\"CallId\"\\s*:\\s*\"([^\"]*)\"");

    // 独立构造而非注入:连接/读各 5s 超时是旁路动作的兜底,
    // 防止外呼无响应挂死 @Async 线程(该线程池与邮件/流推送等监听共用)
    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    /**
     * 对告警责任人发起语音外呼(旁路容错,任何失败仅记日志不抛出)
     */
    public void callAlert(AlertEvent alert, SysUser receiver) {
        String phone = receiver.getPhonenumber();
        if (!StringUtils.hasText(phone)) {
            log.debug("[Voice] 责任人未配手机号,跳过电话外呼: userId={}", receiver.getUserId());
            return;
        }
        // 占位降级:密钥/模板任一未配置即视为未开通外呼,按真实参数构造播报文本走模拟外呼,
        // 保证联调/演示环境链路可验证,不产生真实费用与骚扰电话
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)
                || !StringUtils.hasText(templateCode)) {
            log.info("[Voice] 模拟电话外呼(未配置阿里云密钥): 被叫={}, 播报文本={}", phone, buildText(alert));
            return;
        }
        try {
            String response = doSingleCallByTts(phone, buildText(alert));
            log.info("[Voice] 外呼结果: code={}, callId={}, 被叫={}",
                    extractField(response, CODE_PATTERN), extractField(response, CALL_ID_PATTERN), phone);
        } catch (Exception e) {
            log.error("[Voice] 电话外呼失败: 被叫={}, error={}", phone, e.getMessage());
        }
    }

    /**
     * 真实调用阿里云 SingleCallByTts(RPC API,V1 签名),返回响应体字符串
     */
    private String doSingleCallByTts(String phone, String text) {
        // 业务参数(TtsParam 单文本变量,值是 JSON 字符串,进 form 时整体再 percentEncode)
        Map<String, String> params = new HashMap<>();
        params.put("Action", "SingleCallByTts");
        params.put("Version", "2017-05-25");
        params.put("CalledNumber", phone);
        params.put("TtsCode", templateCode);
        params.put("TtsParam", "{\"tts\":\"" + escapeJson(text) + "\"}");
        if (StringUtils.hasText(calledShowNumber)) {
            params.put("CalledShowNumber", calledShowNumber);
        }
        // RPC 公共参数
        params.put("Format", "JSON");
        params.put("AccessKeyId", accessKeyId);
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", "1.0");
        // Nonce 防重放:每次请求唯一;Timestamp 限制签名有效期
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("Timestamp", UTC_FORMATTER.format(Instant.now()));
        params.put("RegionId", "cn-hangzhou");

        params.put("Signature", sign(params, accessKeySecret));

        // RPC 风格参数走 form 提交;请求体与签名串用同一 percentEncode,
        // 保证服务端 form 解码后的参数与参与签名的参数逐字节一致
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(percentEncode(entry.getKey())).append('=').append(percentEncode(entry.getValue()));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://" + endpoint + "/", new HttpEntity<>(body.toString(), headers), String.class);
        return response.getBody();
    }

    /**
     * 播报一句话(与前端 HeaderAlert TTS 文案风格一致):
     * {设备名}{传感器名}出现{级别}告警,当前数值{value}
     * 名称缺失降级为编码,保证播报可辨识。
     */
    private String buildText(AlertEvent alert) {
        String equipment = StringUtils.hasText(alert.getEquipmentName())
                ? alert.getEquipmentName()
                : (alert.getEquipmentId() != null ? "设备" + alert.getEquipmentId() : "未知设备");
        String sensor = StringUtils.hasText(alert.getSensorName())
                ? alert.getSensorName()
                : (StringUtils.hasText(alert.getSensorCode()) ? alert.getSensorCode() : "未知传感器");
        return equipment + sensor + "出现" + levelChinese(alert.getAlertLevel())
                + "告警,当前数值" + alert.getSensorValue();
    }

    /** 告警级别中文映射(播报可理解;未识别级别原样返回,便于排查) */
    private static String levelChinese(String level) {
        if (level == null) {
            return "未知";
        }
        switch (level) {
            case "SEVERE": return "严重";
            case "WARNING": return "预警";
            case "NORMAL": return "正常";
            default: return level;
        }
    }

    /** 播报文本进 TtsParam JSON 时的最小转义:双引号/反斜杠是 JSON 结构字符,不转会截断参数 */
    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 阿里云 V1 RPC 签名:对全部参数(含 Signature 之前加入的公共/业务参数)计算 HMAC-SHA1
     *
     * @param secret AccessKeySecret(方法内部补 "&" 占位)
     */
    static String sign(Map<String, String> params, String secret) {
        String stringToSign = buildStringToSign(buildCanonicalQuery(params));
        return hmacSha1Base64(stringToSign, secret + "&");
    }

    /**
     * 规范化查询串:参数按 key 字典序(ASCII 升序)拼 percentEncode(k)=percentEncode(v),用 & 连接
     * <p>
     * 为什么必须排序:签名本质是"把参数按双方约定的唯一顺序拼成一个串"再哈希,
     * Map 原始遍历顺序不可复现,字典序是服务端验签时唯一能重建的约定顺序。
     * </p>
     */
    static String buildCanonicalQuery(Map<String, String> params) {
        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            if (canonical.length() > 0) {
                canonical.append('&');
            }
            canonical.append(percentEncode(entry.getKey()))
                    .append('=')
                    .append(percentEncode(entry.getValue()));
        }
        return canonical.toString();
    }

    /**
     * 待签名串 = GET&amp;%2F&amp;percentEncode(规范化查询串)
     * <p>
     * 整个规范化查询串作为路径的同位参数再整体 percentEncode 一次,
     * 保证串内的 = & % 等结构字符不会被误读为分隔符。
     * </p>
     */
    static String buildStringToSign(String canonicalQuery) {
        return "GET&" + percentEncode("/") + "&" + percentEncode(canonicalQuery);
    }

    /**
     * RFC3986 percentEncode(阿里云 V1 签名专用编码)
     * <p>
     * 与标准 URLEncoder(HTML form 编码)的三处差异必须修正,否则验签必失败:
     * 1. 空格:URLEncoder 输出 +,RFC3986 要求 %20;
     * 2. 星号 *:URLEncoder 不编码,RFC3986 要求 %2A;
     * 3. 波浪号 ~:URLEncoder 编码为 %7E,RFC3986 列为非保留字符应保持字面量。
     * </p>
     */
    static String percentEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    /**
     * HMAC-SHA1 后 Base64
     * <p>
     * 密钥为什么是 secret + "&amp;":V1 签名协议规定签名密钥为 AccessKeySecret 再补一个 &amp;,
     * 末尾 &amp; 原为多因子占位(预留 token 位,为空),协议保留至今。
     * </p>
     */
    private static String hmacSha1Base64(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] signBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signBytes);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA1 签名计算失败", e);
        }
    }

    /** 从响应体提取指定 JSON 字段值(简单正则,找不到返回 null) */
    private static String extractField(String body, Pattern pattern) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 签名拼接自测入口(人工核对格式用,不进 CI):
     * 固定参数打印规范化查询串/待签名串/签名值,目测核对三点:
     * 1. 待签名串以 GET&amp;%2F&amp; 三段开头;
     * 2. 参数按 key 字典序排列,TtsParam 的 JSON 结构字符({ } : " ,)与中文均已转义;
     * 3. percentEncode 遵循 RFC3986(空格为 %20 而非 +)。
     */
    public static void main(String[] args) {
        Map<String, String> params = new TreeMap<>();
        params.put("AccessKeyId", "testKeyId");
        params.put("Action", "SingleCallByTts");
        params.put("CalledNumber", "13800000000");
        params.put("Format", "JSON");
        params.put("RegionId", "cn-hangzhou");
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", "9e7f833f-3aa6-4a5c-b3e2-1d4f5a6b7c8d");
        params.put("SignatureVersion", "1.0");
        params.put("Timestamp", "2026-01-01T00:00:00Z");
        params.put("TtsCode", "TTS_TEST_123");
        params.put("TtsParam", "{\"tts\":\"设备1温度传感器出现严重告警,当前数值85.0\"}");
        params.put("Version", "2017-05-25");

        String canonical = buildCanonicalQuery(params);
        String stringToSign = buildStringToSign(canonical);
        System.out.println("canonicalQuery = " + canonical);
        System.out.println("stringToSign   = " + stringToSign);
        System.out.println("signature      = " + sign(params, "testSecret"));
    }
}
