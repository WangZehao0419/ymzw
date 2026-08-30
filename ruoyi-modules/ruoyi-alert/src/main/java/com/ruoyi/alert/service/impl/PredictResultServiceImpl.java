package com.ruoyi.alert.service.impl;

import com.ruoyi.alert.entity.PredictResult;
import com.ruoyi.alert.mapper.PredictResultMapper;
import com.ruoyi.alert.repository.BaseRepository;
import com.ruoyi.alert.service.PredictResultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 预测性维护结果服务实现
 *
 * @author smartartisan
 */
@Service
public class PredictResultServiceImpl extends BaseRepository<PredictResultMapper, PredictResult> implements PredictResultService {

    @Autowired
    private PredictResultMapper baseMapper;

    @Override
    public PredictResultMapper getBaseMapper() {
        return baseMapper;
    }

    /**
     * upsert 不用数据库 ON DUPLICATE KEY:按 sensorCode 查再写,
     * 逻辑显式可控(表的 uk_predict_result_sensor_code 兜底防并发重复)。
     */
    @Override
    public void upsert(PredictResult result) {
        PredictResult existing = lambdaQuery()
                .eq(PredictResult::getSensorCode, result.getSensorCode())
                .one();
        if (existing != null) {
            result.setId(existing.getId());
            updateById(result);
        } else {
            save(result);
        }
    }
}
