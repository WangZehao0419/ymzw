package com.ruoyi.ai.api.domain;

import java.io.Serializable;
import lombok.Data;

/**
 * AI推理请求传输对象：传感器编码 + 检测参数
 * <p>
 * 窗口拉取职责已上收至 ruoyi-ai(数据获取编排):
 * 调用方只传传感器定位与检测参数,历史窗口由 ruoyi-ai 经设备服务拉取后转发 pdm-server,
 * 消除 alert→ruoyi-ai 的窗口冗余搬运。
 * </p>
 *
 * @author smartartisan
 */
@Data
public class AiPredictRequestDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 传感器编码（如 TH-001）
     */
    private String sensorCode;

    /**
     * 异常检测阈值（可空：为空时由 pdm-server 按模型默认阈值判定）
     */
    private Double threshold;

    /**
     * 预测步长（可空：为空时由 pdm-server 按模型默认步长预测）
     */
    private Integer horizon;
}
