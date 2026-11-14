package com.baolong.wsw.mapper;

import com.baolong.wsw.entity.BargainRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BargainRecordMapper extends BaseMapper<BargainRecord> {

    /** 任务被接单 / 取消 / 过期时，把所有待处理报价置为失效 */
    @Update("""
            UPDATE bargain_record
               SET status = 'INVALID', updated_at = NOW()
             WHERE task_id = #{taskId}
               AND status = 'PENDING'
            """)
    int invalidatePending(@Param("taskId") Long taskId);
}
