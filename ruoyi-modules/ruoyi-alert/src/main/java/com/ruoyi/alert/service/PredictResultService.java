package com.ruoyi.alert.service;

import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.ruoyi.alert.entity.PredictResult;

/**
 * 预测性维护结果服务
 *
 * @author smartartisan
 */
public interface PredictResultService extends IRepository<PredictResult> {

    /**
     * 按传感器编号 upsert:存在则 update(沿用主键),否则 insert
     *
     * @param result 待落库的预测结果快照(sensorCode 必填)
     */
    void upsert(PredictResult result);
}
