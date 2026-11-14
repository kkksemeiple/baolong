package com.baolong.wsw.mapper;

import com.baolong.wsw.entity.Post;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    /** 点赞数原子自增，避免「读-改-写」丢更新 */
    @Update("UPDATE post SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{postId}")
    int incrLike(@Param("postId") Long postId, @Param("delta") int delta);

    /** 回复数原子自增 */
    @Update("UPDATE post SET reply_count = GREATEST(reply_count + #{delta}, 0) WHERE id = #{postId}")
    int incrReply(@Param("postId") Long postId, @Param("delta") int delta);
}
