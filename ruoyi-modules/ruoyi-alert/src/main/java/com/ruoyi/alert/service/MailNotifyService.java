package com.ruoyi.alert.service;

import com.ruoyi.alert.entity.AlertEvent;
import com.ruoyi.system.api.domain.SysUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

/**
 * 告警邮件推送服务
 * <p>
 * 邮件是旁路触达渠道:发送失败只记日志,绝不向调用方抛异常,
 * 保证不影响告警落库/流推送主链路。
 * SMTP 未配置(占位)时 JavaMailSender 仍会被装配,但发送必然失败,
 * 故显式判断后跳过,避免无意义的报错刷屏。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
public class MailNotifyService {

    /** 触发时间展示格式,与前端列表展示口径一致 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // JavaMailSender 由 spring-boot-starter-mail 自动装配;@Autowired(required=false)
    // 保证 SMTP 未配置(如本地环境无 spring.mail.host)时应用仍可启动
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromUser;

    /**
     * 发送告警通知邮件(旁路容错,任何失败仅记日志不抛出)
     */
    public void sendAlertMail(AlertEvent alert, SysUser receiver) {
        // SMTP 未配置(无 JavaMailSender Bean 或发件账号为空):跳过而非报错
        if (mailSender == null || !StringUtils.hasText(fromUser)) {
            log.warn("[Mail] SMTP 未配置,跳过邮件推送: to={}", receiver.getEmail());
            return;
        }
        if (!StringUtils.hasText(receiver.getEmail())) {
            log.debug("[Mail] 责任人未配邮箱,跳过邮件推送: userId={}", receiver.getUserId());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromUser);
            message.setTo(receiver.getEmail());
            message.setSubject(buildSubject(alert));
            message.setText(buildText(alert));
            mailSender.send(message);
            log.info("[Mail] 告警邮件已发送: to={}", receiver.getEmail());
        } catch (Exception e) {
            // 邮件失败不抛出,电话外呼仍继续
            log.error("[Mail] 告警邮件发送失败: to={}, error={}", receiver.getEmail(), e.getMessage());
        }
    }

    /**
     * 邮件主题: [告警] {设备名}-{传感器名} {级别中文}
     * <p>
     * 名称缺失时降级为编码,保证主题可辨识。
     * </p>
     */
    private String buildSubject(AlertEvent alert) {
        String equipment = StringUtils.hasText(alert.getEquipmentName())
                ? alert.getEquipmentName() : String.valueOf(alert.getEquipmentId());
        String sensor = StringUtils.hasText(alert.getSensorName())
                ? alert.getSensorName() : String.valueOf(alert.getSensorCode());
        return "[告警] " + equipment + "-" + sensor + " " + levelChinese(alert.getAlertLevel());
    }

    /**
     * 邮件正文: 多行纯文本,承载比主题更完整的关键信息
     */
    private String buildText(AlertEvent alert) {
        return "设备: " + (StringUtils.hasText(alert.getEquipmentName())
                ? alert.getEquipmentName() : alert.getEquipmentId()) + "\n"
                + "传感器: " + (StringUtils.hasText(alert.getSensorName())
                ? alert.getSensorName() : alert.getSensorCode()) + "\n"
                + "告警级别: " + levelChinese(alert.getAlertLevel()) + "\n"
                + "当前数值: " + alert.getSensorValue() + "\n"
                + "触发时间: " + (alert.getTriggerTime() == null ? "-" : TIME_FORMATTER.format(alert.getTriggerTime()));
    }

    /**
     * 告警级别中文映射(未识别级别原样返回,便于排查)
     */
    private String levelChinese(String level) {
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
