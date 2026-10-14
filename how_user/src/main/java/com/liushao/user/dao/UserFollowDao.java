package com.liushao.user.dao;

import com.liushao.user.pojo.UserFollow;
import com.liushao.user.pojo.UserFollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 用户关注唯一关系；INSERT IGNORE ... SELECT 保证被关注用户存在且同一关注关系只登记一次。
 */
public interface UserFollowDao extends JpaRepository<UserFollow, UserFollowId> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO tb_user_follow (follower_id, followee_id, created_at) "
            + "SELECT :followerId, :followeeId, UTC_TIMESTAMP(6) FROM tb_user WHERE id = :followeeId", nativeQuery = true)
    int insertIfAbsent(@Param("followerId") String followerId, @Param("followeeId") String followeeId);

    @Modifying
    @Query(value = "DELETE FROM tb_user_follow WHERE follower_id = :followerId AND followee_id = :followeeId",
            nativeQuery = true)
    int deleteRelation(@Param("followerId") String followerId, @Param("followeeId") String followeeId);
}
