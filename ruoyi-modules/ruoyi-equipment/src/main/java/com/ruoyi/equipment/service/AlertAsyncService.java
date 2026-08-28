package com.ruoyi.equipment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 异步告警服务 —— 演示 @Async 用法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertAsyncService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${alert.email.to}")
    private String to;

    /**
     * 异步方法：在独立线程池中执行，不阻塞主线程
     * 这里简单打印日志模拟告警通知
     */
    @Async
    public void sendAlert(Integer equipmentId, LocalDateTime triggerTime) {
        String threadName = Thread.currentThread().getName();
        log.info("[异步线程: {}] 开始处理设备 {} 的告警，触发时间: {}",
                threadName, equipmentId, triggerTime);

        try {
            Thread.sleep(5000);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("[母机新生] 告警测试邮件");
            message.setText(
                    "设备名称：测试立式加工中心\n" +
                    "传感器编号：SP-001\n" +
                    "传感器名称：测试振幅传感器\n" +
                    "智能诊断报告：\n" +
                            "\t告警解释：振动幅值在1秒内从0.53骤升至5.0，峰值5.65，持续约10秒后指数衰减至正常，属孤立瞬态冲击。\n" +
                            "\t根因分析：脉冲式上升、快速衰减的波形符合外部机械冲击特征（如刀具撞硬点、切屑卡入）或传感器瞬时干扰。异常后振动完全恢复，排除持续故障。\n" +
                            "\t处置建议：暂不停机，立即检查刀具与切屑情况，调取同期电流信号辅助确认。24小时内加强监控，若无复现则关闭告警。\n" +
                    "触发时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );

            mailSender.send(message);
            log.info("告警邮件已发送至: {}", to);
        } catch (Exception e) {
            log.error("邮件发送失败", e);
        }
        log.info("[异步线程: {}] 设备 {} 告警处理完成", threadName, equipmentId);
    }
}
