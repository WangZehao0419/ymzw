package com.ruoyi.alert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.alert.entity.PredictAlert;
import com.ruoyi.alert.entity.PredictResult;
import com.ruoyi.alert.mapper.PredictAlertMapper;
import com.ruoyi.alert.service.PredictResultService;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预测结果内部接口 Controller（内部服务调用）
 * <p>
 * 供 ruoyi-ai 等模块经 OpenFeign 拉取单传感器最新预测结果与预测告警,
 * 端点由 @InnerAuth 保护,仅限服务间携带内部凭证的调用,不对网关外暴露。
 * 仿 equipment 模块 inner controller 模式(InnerSensorHistoryController)。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@RestController
@RequestMapping("/inner/predict")
@RequiredArgsConstructor
public class InnerPredictController {

    private final PredictResultService predictResultService;
    private final PredictAlertMapper predictAlertMapper;

    /**
     * 查询传感器最新预测结果 + 最近一条 PREDICT 告警摘要（内部服务调用，@InnerAuth 保护）
     * <p>
     * predict_result 按传感器唯一行(uk_predict_result_sensor_code),即最新快照;
     * 告警取 predict_alert(本表恒为 PREDICT 类型)按触发时间倒序首条,无告警史为 null。
     * </p>
     *
     * @param sensorCode 传感器编号（如 TH-001）
     * @param source     请求来源
     * @return 最新预测结果与最近预测告警
     */
    @InnerAuth
    @GetMapping("/result/latest/{sensorCode}")
    public R<LatestVO> latestResult(@PathVariable("sensorCode") String sensorCode,
                                    @RequestHeader(SecurityConstants.FROM_SOURCE) String source) {
        PredictResult result = predictResultService.lambdaQuery()
                .eq(PredictResult::getSensorCode, sensorCode)
                .one();
        if (result == null) {
            return R.fail("该传感器暂无预测结果");
        }
        LatestVO vo = new LatestVO();
        vo.setResult(result);
        vo.setAlert(predictAlertMapper.selectList(new LambdaQueryWrapper<PredictAlert>()
                        .eq(PredictAlert::getSensorCode, sensorCode)
                        .orderByDesc(PredictAlert::getTriggerTime))
                .stream().findFirst().orElse(null));
        return R.ok(vo);
    }

    /**
     * 最新预测结果响应
     */
    @Data
    public static class LatestVO {

        /** 最新预测快照(含 anomaly_score/rul_earliest/rul_latest/model_version 等模型推理字段) */
        private PredictResult result;

        /** 最近一条 PREDICT 告警摘要(级别/状态/触发与失效时刻等;无告警史为 null) */
        private PredictAlert alert;
    }
}
