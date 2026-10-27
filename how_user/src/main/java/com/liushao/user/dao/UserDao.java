package com.liushao.user.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.liushao.user.pojo.User;

/**
 * @author huangshen
 */
public interface UserDao extends JpaRepository<User, String> {

	List<User> findAllByMobile(String mobile);

	@Query(value = "SELECT * FROM tb_user WHERE mobile = :mobile FOR UPDATE", nativeQuery = true)
	List<User> findAllByMobileForUpdate(@Param("mobile") String mobile);

	@Query(value = "SELECT * FROM tb_user WHERE id = :id FOR UPDATE", nativeQuery = true)
	Optional<User> findByIdForUpdate(@Param("id") String id);

	@Query(value = "SELECT u.* FROM tb_user u JOIN tb_user_follow f ON f.follower_id = u.id "
			+ "WHERE f.followee_id = :userId ORDER BY f.created_at DESC, u.id",
			countQuery = "SELECT COUNT(*) FROM tb_user_follow WHERE followee_id = :userId", nativeQuery = true)
	Page<User> findFollowers(@Param("userId") String userId, Pageable pageable);

	@Query(value = "SELECT u.* FROM tb_user u JOIN tb_user_follow f ON f.followee_id = u.id "
			+ "WHERE f.follower_id = :userId ORDER BY f.created_at DESC, u.id",
			countQuery = "SELECT COUNT(*) FROM tb_user_follow WHERE follower_id = :userId", nativeQuery = true)
	Page<User> findFollowees(@Param("userId") String userId, Pageable pageable);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE tb_user SET fanscount = COALESCE(fanscount, 0) + 1 WHERE id = :id", nativeQuery = true)
	int incrementFans(@Param("id") String id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE tb_user SET followcount = COALESCE(followcount, 0) + 1 WHERE id = :id", nativeQuery = true)
	int incrementFollow(@Param("id") String id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE tb_user SET fanscount = fanscount - 1 WHERE id = :id AND fanscount > 0", nativeQuery = true)
	int decrementFans(@Param("id") String id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE tb_user SET followcount = followcount - 1 WHERE id = :id AND followcount > 0", nativeQuery = true)
	int decrementFollow(@Param("id") String id);
}
