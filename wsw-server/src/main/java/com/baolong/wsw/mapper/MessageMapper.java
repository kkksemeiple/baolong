package com.baolong.wsw.mapper;

import com.baolong.wsw.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /** 把某个会话里发给我的消息全部标为已读 */
    @Update("""
            UPDATE message
               SET read_flag = 1
             WHERE conversation_id = #{conversationId}
               AND to_user_id = #{userId}
               AND read_flag = 0
            """)
    int markRead(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}
