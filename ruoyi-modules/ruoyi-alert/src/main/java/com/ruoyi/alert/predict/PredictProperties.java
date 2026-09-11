package com.ruoyi.alert.predict;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 预测性维护配置
 * <p>
 * B4 起检测链路由统计算法(CUSUM/MAD/趋势外推)切换为模型推理
 * (Feign→ruoyi-ai→pdm-server),统计算法相关配置(基线/检测阈值/拟合参数)随之删除;
 * T2 起历史窗口由 ruoyi-ai 拉取,窗口点数配置(windowPoints)随之删除,
 * 只保留调度通用项、状态机参数和模型推理子配置。
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

    /** 预测任务调度间隔(毫秒,@Scheduled fixedDelay,上轮结束后间隔该时长再跑) */
    private long intervalMs = 30000;

    /** 入态连续异常轮数:isAnomaly 连续 true 达到该轮数才入态 DEGRADING(替代原 L2 突变单轮触发,防模型单轮毛刺误报) */
    private int anomalyRounds = 2;

    /** RUL 推后退出阈值(分钟):DEGRADING 态 RUL 较上轮推后超过该值视为劣化放缓,幽灵退出回 NORMAL(防长期挂不兑现的预测) */
    private long rulDeferExitMinutes = 60;

    /** 模型推理子配置 */
    private Model model = new Model();

    /**
     * 模型推理子配置
     *
     * @author smartartisan
     */
    @Data
    public static class Model {

        /** 预测步长(分钟):单次推理向前外推的时长,与 RUL(rulPoint 等)的分钟口径一致 */
        private int horizon = 48;
    }
}
