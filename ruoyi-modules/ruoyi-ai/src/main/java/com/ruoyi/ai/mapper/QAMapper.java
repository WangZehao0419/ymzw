package com.ruoyi.ai.mapper;

import com.ruoyi.ai.entity.QA;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 问答记录Mapper接口
 * <p>
 * 提供问答记录（qna_management表）的数据库操作方法
 * </p>
 *
 * @author smartartisan
 */
@Mapper
public interface QAMapper {

    /**
     * 插入问答记录
     *
     * @param qa 问答记录实体
     * @return 影响行数
     */
    int insertQa(QA qa);

    /**
     * 根据ID查询问答记录
     *
     * @param qnaId 问答记录ID
     * @return 问答记录
     */
    QA findByQnaId(@Param("qnaId") String qnaId);

    /**
     * 分页查询问答记录
     *
     * @param userId    用户ID（可选）
     * @param pageStart 起始位置
     * @param pageSize  每页大小
     * @return 问答记录列表
     */
    List<Map<String, Object>> getQARecords(@Param("userId") String userId,
                                            @Param("pageStart") int pageStart,
                                            @Param("pageSize") int pageSize);

    /**
     * 查询问答记录总数
     *
     * @param keyword 搜索关键词（可选）
     * @return 总数
     */
    int getQARecordsTotal(@Param("keyword") String keyword);

    /**
     * 更新问答记录
     *
     * @param qa 问答记录实体
     * @return 影响行数
     */
    int updateQA(QA qa);

    /**
     * 删除问答记录
     *
     * @param qnaId 问答记录ID
     * @return 影响行数
     */
    int deleteQA(@Param("qnaId") String qnaId);
}
