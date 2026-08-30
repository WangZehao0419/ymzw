package com.ruoyi.alert.predict;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 预测性维护配置
 * <p>
 * 取数与检测参数统一收敛于此:PredictTask 的调度间隔/窗口点数、
 * 基线学习的正弦周期、后续任务(Task 4/5)检测器的阈值与 AI 预测端点。
 * 配置块位于 application.yml 的 predict 前缀,enabled 默认关闭,演示时开启。
 * </p>
 *
 * @author smartartisan
 */
@Data
@Component
@ConfigurationProperties(prefix = "predict")
public class PredictProperties {

    /** 总开关:关闭时 PredictTask 每轮调度开头直接返回(空转,不拉数据不落库) */
    private boolean enabled = false;

    /** 历史窗口点数:单传感器每轮经 Feign 拉取的最近时序点数(需≥3倍sine-period,前1/3用于基线学习) */
    private int windowPoints = 600;

    /** 正弦周期点数:同相位残差 val(i)-val(i-period) 的对齐周期,与模拟器 SINE_PERIOD_CYCLES 联动 */
    private int sinePeriod = 60;

    /** 预测任务调度间隔(毫秒,@Scheduled fixedDelay,上轮结束后间隔该时长再跑) */
    private long intervalMs = 30000;

    /** MAD 比值阈值:|x-median|/MAD 超过判定突发(Task 4 MadDetector) */
    private double madRatioThreshold = 2.0;

    /** CUSUM 允差 k(Task 4 CusumDetector) */
    private double cusumK = 0.5;

    /** CUSUM 决策阈值 h(Task 4 CusumDetector) */
    private double cusumH = 5.0;

    /** WLS 衰减因子:越接近1历史权重越高,趋势越平滑(Task 5 TrendExtrapolator) */
    private double wlsLambda = 0.99;

    /** 趋势拟合优度阈值:R2 低于该值不做外推(Task 5 TrendExtrapolator) */
    private double r2Threshold = 0.8;

    /** 最小有效斜率:低于该值视为无趋势(Task 5 TrendExtrapolator) */
    private double minSlope = 0.005;

    /** T1 阶段最大持续点数:超过则状态机升级(Task 5 状态机) */
    private int t1MaxPoints = 2400;

    /** T1 退出延迟点数:连续恢复该点数才退出 T1,防抖(Task 5 状态机) */
    private int t1DeferExitPoints = 600;

    /** AI 预测子配置 */
    private Ai ai = new Ai();

    /** 健康度聚合权重:键与 Task 5 聚合组件对齐(如 stat/trend/ai),未配置时为空 Map */
    private Map<String, Double> healthWeights = new HashMap<>();

    /**
     * AI 预测子配置
     *
     * @author smartartisan
     */
    @Data
    public static class Ai {

        /** AI 预测开关:未部署 AI 服务时保持 false,仅走统计+趋势外推 */
        private boolean enabled = false;

        /** AI 预测服务地址(如 http://localhost:8000) */
        private String baseUrl = "";

        /** AI 预测调用超时(毫秒) */
        private long timeoutMs = 3000;

        /** AI 预测时域(外推点数) */
        private int horizon = 60;
    }
}
