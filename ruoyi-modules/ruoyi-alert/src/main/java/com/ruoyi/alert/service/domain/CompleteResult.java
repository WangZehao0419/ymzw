package com.ruoyi.alert.service.domain;

import lombok.Data;

/**
 * 工单完成结果
 * <p>
 * 工单本身完成与"联动复位"是两个独立成败维度:
 * 工单状态置 COMPLETED 是本地事务必然成功,而复位指令经 Feign 下发
 * 设备服务可能失败,前端需要区分展示"工单已完成但复位失败需人工处理"。
 * </p>
 *
 * @author smartartisan
 */
@Data
public class CompleteResult {

    /** 联动复位是否成功(含"未配置联动复位"的跳过场景) */
    private boolean resetSuccess;

    /** 复位结果说明(成功/失败原因/跳过原因) */
    private String resetMessage;

    public CompleteResult() {
    }

    public CompleteResult(boolean resetSuccess, String resetMessage) {
        this.resetSuccess = resetSuccess;
        this.resetMessage = resetMessage;
    }
}
