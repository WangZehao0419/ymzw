package com.ruoyi.alert.event.listener;

import com.ruoyi.alert.event.SensorDataReceivedEvent;
import com.ruoyi.alert.service.AlertDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * L1 规则判定监听器
 * <p>
 * 在数据落库(Order=0)之后执行,毫秒级同步判定。
 * 后续 L2 统计检测监听器(StatDetectionListener)将以同样方式挂在 Order=2。
 * </p>
 *
 * @author smartartisan
 */
@Component
@RequiredArgsConstructor
public class RuleDetectionListener {

    private final AlertDetectionService detectionService;

    @EventListener
    @Order(1)
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        detectionService.detect(event);
    }
}
