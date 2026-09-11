package com.ruoyi.alert.predict;

import com.ruoyi.ai.api.RemoteAiService;
import com.ruoyi.ai.api.domain.AiPredictRequestDTO;
import com.ruoyi.ai.api.domain.AiPredictResultDTO;
import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.entity.PredictResult;
import com.ruoyi.alert.service.PredictResultService;
import com.ruoyi.alert.service.RuleService;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预测性维护主任务
 * <p>
 * B4 起检测链路由统计算法(基线/MAD/CUSUM/趋势外推)切换为模型推理,
 * 数据获取职责已上收至 ruoyi-ai(T2):本任务不再拉取历史窗口。
 * 每轮调度:Feign 拉全量传感器列表 → 逐传感器构造推理请求
 * (编码 + 启用规则阈值 + 步长) → 经 RemoteAiService 调 ruoyi-ai
 * (拉窗后转发 pdm-server) → isAnomaly 持续性驱动状态机推进
 * (内部处理 PREDICT 告警发出/升级/恢复) → 落 predict_result 快照;
 * 告警展示值取响应回显 window 的末点(模型输入的最新采样)。
 * Feign 无 fallback(用户决策不做降级容错):推理失败自然抛异常,
 * 该传感器本轮中断,不产出任何结论。
 * 单传感器失败只跳过该传感器本轮,单行 warn 无堆栈,不让异常中断整轮。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PredictTask {

    private final PredictProperties props;
    private final RemoteEquipmentService remoteEquipmentService;
    private final RemoteAiService remoteAiService;
    private final PredictResultService predictResultService;
    private final RuleService ruleService;
    private final PredictStateMachine stateMachine;

    /**
     * 预测性维护主循环(间隔 predict.interval-ms,上轮结束后起算)
     */
    @Scheduled(fixedDelayString = "${predict.interval-ms:30000}")
    public void run() {
        // 总开关:默认关闭,演示/联调时在 application.yml 置 true
        if (!props.isEnabled()) {
            return;
        }
        R<List<SensorMetaDTO>> sensorsResult = remoteEquipmentService.listAllSensors(SecurityConstants.INNER);
        if (sensorsResult == null || R.FAIL == sensorsResult.getCode()
                || sensorsResult.getData() == null || sensorsResult.getData().isEmpty()) {
            // 全量列表失败/为空:本轮直接放弃(逐传感器无从谈起)
            log.warn("[PREDICT] 传感器全量列表获取失败或为空,跳过本轮");
            return;
        }
        for (SensorMetaDTO sensor : sensorsResult.getData()) {
            processSensor(sensor);
        }
    }

    /**
     * 单传感器推理推进(取数由 ruoyi-ai 编排,本侧只发编码与检测参数)
     */
    private void processSensor(SensorMetaDTO sensor) {
        String sensorCode = sensor.getSensorCode();
        try {
            // 阈值取该传感器启用的阈值规则(与页面 detail 同查询同排序,同传感器同轮结果一致)
            AlertRule rule = findRule(sensor);
            AiPredictRequestDTO request = buildRequest(
                    sensor, rule, props.getModel().getHorizon());
            R<AiPredictResultDTO> aiResp = remoteAiService.predict(request, SecurityConstants.INNER);
            if (aiResp == null || R.FAIL == aiResp.getCode() || aiResp.getData() == null) {
                // 无降级容错(用户决策):本轮该传感器不产出任何结论,异常交给外层 catch 记录
                throw new IllegalStateException("模型推理失败: "
                        + (aiResp == null ? "无响应" : aiResp.getMsg()));
            }
            AiPredictResultDTO ai = aiResp.getData();
            if (ai.getWindow() == null || ai.getWindow().isEmpty()) {
                // 响应未带回显窗口:无法定位最新采样,按原空窗语义跳过本轮(不推进状态机不落库)
                log.warn("[PREDICT] 推理响应无窗口数据,跳过本轮: sensorCode={}", sensorCode);
                return;
            }

            // 状态机推进:内部处理 PREDICT 告警发出/升级/幽灵退出,返回最新状态
            // 告警展示值取回显窗口末点(模型输入的最新采样)
            double lastValue = ai.getWindow().get(ai.getWindow().size() - 1);
            String status = stateMachine.advance(sensor, ai, lastValue);

            upsertSnapshot(sensor, status, ai);
        } catch (Exception e) {
            // 单传感器异常只跳过本轮该传感器,单行 warn 不带堆栈
            log.warn("[PREDICT] 传感器处理异常,跳过本轮: sensorCode={}, error={}", sensorCode, e.getMessage());
        }
    }

    /**
     * 查该传感器启用的阈值规则
     * <p>
     * 不用 one():同传感器多条启用规则时 one() 抛 TooManyResultsException,
     * 会被 processSensor 的 catch 吞掉导致该传感器整轮检测中断,改取首条;
     * 按 id 升序取首条,保证任务与 detail 页面在不同查询下取到同一条规则
     * (推理输入一致 → 结果一致)。
     * </p>
     */
    private AlertRule findRule(SensorMetaDTO sensor) {
        return ruleService.lambdaQuery()
                .eq(AlertRule::getSensorId, sensor.getId())
                .eq(AlertRule::getEnabled, 1)
                .orderByAsc(AlertRule::getId)
                .list().stream().findFirst().orElse(null);
    }

    /**
     * 构造推理请求(PredictTask 与 PredictController.detail 共用:
     * 同传感器同轮走完全相同的输入,保证两处推理结果一致)
     * <p>
     * 历史窗口由 ruoyi-ai 拉取(T2 数据获取上收),此处只传编码与检测参数。
     * </p>
     *
     * @param sensor  传感器元数据
     * @param rule    启用的阈值规则(可为 null)
     * @param horizon 预测步长(分钟)
     * @return 推理请求
     */
    public static AiPredictRequestDTO buildRequest(SensorMetaDTO sensor, AlertRule rule, int horizon) {
        AiPredictRequestDTO request = new AiPredictRequestDTO();
        request.setSensorCode(sensor.getSensorCode());
        request.setThreshold(thresholdOf(rule));
        request.setHorizon(horizon);
        return request;
    }

    /**
     * 检测阈值:优先上限,无上限取下限(与原趋势外推同优先序,退化场景为上漂越上限);
     * 无规则不传,pdm-server 按模型默认阈值判定
     */
    static Double thresholdOf(AlertRule rule) {
        if (rule == null) {
            return null;
        }
        return rule.getUpperLimit() != null ? rule.getUpperLimit() : rule.getLowerLimit();
    }

    /**
     * RUL 点估计(分钟) → 预计失效时刻;null 直通(模型未产出 RUL)
     */
    static LocalDateTime toPredictedBreachTime(Long rulPoint, LocalDateTime now) {
        return rulPoint == null ? null : now.plusMinutes(rulPoint);
    }

    /**
     * 落本轮快照
     * <p>
     * NORMAL 态失效预测字段必须清空:upsert 走 MP updateById 非空更新,字段为 null 时
     * 保留库中旧值,幽灵退出/状态回落后残留的旧预测值会误导前端继续展示过期告警。
     * anomalyScore/healthScore/modelVersion 例外:它们是本轮推理的即时量,与状态机正交,
     * NORMAL 态也有本轮值——与 RUL 字段生命周期不同,两条路径都更新而非清空。
     * 统计旧列(slope/t1/onset/band)在回 NORMAL 时顺带显式清空:
     * 统计链路下线后不再写入,不清会残留切换前的旧值误导排查。
     * </p>
     */
    private void upsertSnapshot(SensorMetaDTO sensor, String status, AiPredictResultDTO ai) {
        PredictResult result = new PredictResult();
        result.setSensorCode(sensor.getSensorCode());
        result.setEquipmentId(sensor.getEquipmentId());
        result.setStatus(status);
        result.setHealthScore(ai.getHealthScore());
        result.setAnomalyScore(ai.getAnomalyScore());
        result.setRulEarliest(ai.getRulEarliest());
        result.setRulLatest(ai.getRulLatest());
        result.setModelVersion(ai.getModelVersion());
        // 显式赋值;MyMetaObjectHandler 的 strict 填充不会覆盖非空值
        result.setUpdateTime(LocalDateTime.now());
        if ("NORMAL".equals(status)) {
            // NORMAL 态无失效预测语义:insert 路径同样不落 RUL 字段,保证两条路径口径一致
            result.setPredictedBreachTime(null);
            result.setRulEarliest(null);
            result.setRulLatest(null);
        } else {
            result.setPredictedBreachTime(toPredictedBreachTime(ai.getRulPoint(), LocalDateTime.now()));
        }
        PredictResult existing = predictResultService.lambdaQuery()
                .eq(PredictResult::getSensorCode, sensor.getSensorCode())
                .one();
        if (existing != null && "NORMAL".equals(status)) {
            // 已存在记录回 NORMAL:lambdaUpdate 显式 set null 清掉残留旧值
            // (updateById 非空更新清不掉;记录不存在时无残留,走下方 upsert 插入即可)
            predictResultService.lambdaUpdate()
                    .eq(PredictResult::getSensorCode, sensor.getSensorCode())
                    .set(PredictResult::getPredictedBreachTime, null)
                    .set(PredictResult::getRulEarliest, null)
                    .set(PredictResult::getRulLatest, null)
                    .set(PredictResult::getSlope, null)
                    .set(PredictResult::getT1Points, null)
                    .set(PredictResult::getOnsetTime, null)
                    .set(PredictResult::getBandJson, null)
                    .set(PredictResult::getStatus, "NORMAL")
                    .set(PredictResult::getHealthScore, ai.getHealthScore())
                    .set(PredictResult::getAnomalyScore, ai.getAnomalyScore())
                    .set(PredictResult::getModelVersion, ai.getModelVersion())
                    .set(PredictResult::getEquipmentId, sensor.getEquipmentId())
                    .set(PredictResult::getUpdateTime, LocalDateTime.now())
                    .update();
            return;
        }
        predictResultService.upsert(result);
    }
}
