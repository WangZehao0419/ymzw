package com.ruoyi.ai.api.domain;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * AI推理结果传输对象：异常评分/健康分/分位数预测/RUL + 窗口回显
 * <p>
 * 推理字段与 pdm-server 推理进程的 JSON 响应同构,ruoyi-ai 代理层直接反序列化透传。
 * anomalyScore 为无量纲比值(×阈值倍数,>1 即异常),跨传感器可比。
 * RUL 三项可空：模型未产出剩余寿命时为 null,调用方按无 RUL 结论处理。
 * window/windowTs 由 ruoyi-ai 编排层从拉取结果填充(数据获取上收后
 * pdm-server 不再回显窗口):调用方(alert)展示与取值(末点)以此为唯一数据源。
 * </p>
 *
 * @author smartartisan
 */
@Data
public class AiPredictResultDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 推理输入窗口数值（升序,最新在末尾;pdm-server 原样回显）
     */
    private List<Double> window;

    /**
     * 推理输入窗口时间戳（epoch 毫秒,与 window 下标一一对应）
     */
    private List<Long> windowTs;

    /**
     * 异常评分（无量纲比值,×阈值倍数,>1 即异常,跨传感器可比）
     */
    private Double anomalyScore;

    /**
     * 是否异常
     */
    private Boolean isAnomaly;

    /**
     * 健康评分
     */
    private Double healthScore;

    /**
     * P10 分位预测序列（悲观界）
     */
    private List<Double> q10;

    /**
     * P50 分位预测序列（中位数）
     */
    private List<Double> q50;

    /**
     * P90 分位预测序列（乐观界）
     */
    private List<Double> q90;

    /**
     * RUL 点估计（可空,epoch 毫秒或分钟粒度由调用双方约定）
     */
    private Long rulPoint;

    /**
     * RUL 最早失效时刻（可空）
     */
    private Long rulEarliest;

    /**
     * RUL 最晚失效时刻（可空）
     */
    private Long rulLatest;

    /**
     * 模型版本（结果溯源与模型迭代比对）
     */
    private String modelVersion;

    /**
     * 推理耗时（毫秒）
     */
    private Long inferenceMs;
}
