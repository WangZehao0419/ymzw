package com.ruoyi.inspection.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.inspection.repository.BaseRepository;
import com.ruoyi.inspection.entity.InspectionRecord;
import com.ruoyi.inspection.mapper.InspectionRecordMapper;
import com.ruoyi.inspection.service.InspectionRecordService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检测记录 Service 实现类
 * <p>
 * 继承 MyBatis-Plus 的 ServiceImpl，实现业务逻辑
 * </p>
 *
 * @author smartartisan
 */
@Service
public class InspectionRecordServiceImpl extends BaseRepository<InspectionRecordMapper, InspectionRecord> implements InspectionRecordService {
    @org.springframework.beans.factory.annotation.Autowired
    private InspectionRecordMapper baseMapper;

    @Override
    public InspectionRecordMapper getBaseMapper() {
        return baseMapper;
    }


    @Override
    public List<InspectionRecord> listByPartId(Long partId) {
        // 使用 LambdaQueryWrapper 进行条件查询，按检测时间降序排列
        LambdaQueryWrapper<InspectionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InspectionRecord::getPartId, partId)
                .orderByDesc(InspectionRecord::getInspectionTime);
        return this.list(wrapper);
    }
}
