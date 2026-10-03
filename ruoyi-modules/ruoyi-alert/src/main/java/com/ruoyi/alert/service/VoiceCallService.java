package com.ruoyi.alert.service;

import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.system.api.domain.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * 告警电话外呼服务
 * <p>
 * 对外契约 callAlert(AlertEvent, SysUser) 不变,调用方 AlertNotifyListener 零感知。
 * 真实外呼路径 = Stepone AI(见 SteponeVoiceClient,白名单硬闸+号码规范化)。
 * 同日按用户裁定移除模拟外呼分支——告警即真实拨号,联调时白名单号码会被真实拨打。
 * 外呼是旁路触达渠道:任何失败只记日志,绝不向调用方抛异常。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceCallService {

    /**
     * 真实外呼白名单(逗号分隔团队手机号;空=禁止一切真实外呼)
     * <p>
     * 硬闸语义:白名单外号码一律拦截并记日志,防库内脏数据/误配责任人产生骚扰与费用。
     * 值经 Nacos 下发(配置唯一来源),非 Java 常量,便于演示前热调。
     * </p>
     */
    @Value("${alert.notify.voice.whitelist:}")
    private String whitelistRaw;

    /** Stepone REST 客户端(构造注入):承接真实外呼 */
    private final SteponeVoiceClient steponeClient;

    /**
     * 对告警责任人发起语音外呼(旁路容错,任何失败仅记日志不抛出)
     */
    public void callAlert(AlertEvent alert, SysUser receiver) {
        String phone = receiver.getPhonenumber();
        if (!StringUtils.hasText(phone)) {
            log.debug("[Voice] 责任人未配手机号,跳过电话外呼: userId={}", receiver.getUserId());
            return;
        }
        try {
            if (!steponeClient.isConfigured()) {
                // 未配置降级:无 key 无法外呼,明确记日志跳过
                log.warn("[Voice] Stepone api-key 未配置,跳过外呼: 被叫={}", phone);
                return;
            }
            String normalized = normalizePhone(phone);
            if (normalized == null) {
                log.warn("[Voice] 非法手机号,跳过真实外呼: 被叫={}", phone);
                return;
            }
            if (!whitelistSet().contains(normalized)) {
                log.warn("[Voice] 白名单拦截(真实外呼仅放行团队手机): 被叫={}, 播报文本={}", phone, buildText(alert));
                return;
            }
            // 失败不重试(铁律):平台 Idempotency-Key 服务端不承诺去重,
            // 盲重试=重复呼叫真人事故;异常统一抛给外层记日志,call_id 已在其内部留存
            steponeClient.callAnnouncement(normalized, buildText(alert));
        } catch (Exception e) {
            // 整体旁路容错:外呼失败不影响落库/推送/邮件主链路
            log.error("[Voice] 电话外呼失败: 被叫={}, error={}", phone, e.getMessage());
        }
    }

    /**
     * 号码规范化:去 +86/86 前缀与首尾空白,校验 11 位手机号格式;非法返回 null
     */
    static String normalizePhone(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String p = raw.trim();
        if (p.startsWith("+86")) {
            p = p.substring(3);
        } else if (p.startsWith("86") && p.length() == 13) {
            p = p.substring(2);
        }
        return p.matches("1[3-9]\\d{9}") ? p : null;
    }

    /**
     * 白名单集合:逐个规范化后收集;配置为空=空集合=禁止一切真实外呼(故障安全默认)
     */
    private Set<String> whitelistSet() {
        Set<String> set = new HashSet<>();
        if (!StringUtils.hasText(whitelistRaw)) {
            return set;
        }
        for (String item : whitelistRaw.split(",")) {
            String n = normalizePhone(item);
            if (n != null) {
                set.add(n);
            }
        }
        return set;
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
            case "CRITICAL": return "危急";
            case "SEVERE": return "严重";
            case "IMPORTANT": return "重要";
            case "WARNING": return "预警";
            case "NORMAL": return "正常";
            default: return level;
        }
    }
}
