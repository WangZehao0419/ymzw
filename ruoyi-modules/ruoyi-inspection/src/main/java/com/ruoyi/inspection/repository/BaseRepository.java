package com.ruoyi.inspection.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;

import java.util.Collection;
import java.util.function.BiFunction;
import org.apache.ibatis.session.SqlSession;

/**
 * 业务模块通用 Repository 基类
 * <p>
 * MyBatis-Plus 3.5.17 SB4 starter 的 AbstractRepository 把 saveBatch/saveOrUpdateBatch/updateBatchById
 * 留为 abstract，子类必须实现。本类用 executeBatch + BaseMapper 标准实现统一处理，
 * 各业务 ServiceImpl 继承本类即可获得完整 CRUD 能力。
 * </p>
 *
 * @author ruoyi
 */
public abstract class BaseRepository<M extends BaseMapper<T>, T> extends AbstractRepository<M, T> {

    @Override
    public boolean saveBatch(Collection<T> entityList, int batchSize) {
        return executeBatch(entityList, batchSize, (BiFunction<SqlSession, T, Integer>) (sqlSession, entity) -> {
            M mapper = sqlSession.getMapper(getMapperClass());
            return mapper.insert(entity);
        });
    }

    @Override
    public boolean saveOrUpdateBatch(Collection<T> entityList, int batchSize) {
        return executeBatch(entityList, batchSize, (BiFunction<SqlSession, T, Integer>) (sqlSession, entity) -> {
            M mapper = sqlSession.getMapper(getMapperClass());
            int updated = mapper.updateById(entity);
            return updated > 0 ? updated : mapper.insert(entity);
        });
    }

    @Override
    public boolean updateBatchById(Collection<T> entityList, int batchSize) {
        return executeBatch(entityList, batchSize, (BiFunction<SqlSession, T, Integer>) (sqlSession, entity) -> {
            M mapper = sqlSession.getMapper(getMapperClass());
            return mapper.updateById(entity);
        });
    }
}
