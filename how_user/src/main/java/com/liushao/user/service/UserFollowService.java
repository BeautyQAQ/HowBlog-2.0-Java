package com.liushao.user.service;

import com.liushao.entity.PageResult;
import com.liushao.user.dao.UserDao;
import com.liushao.user.dao.UserFollowDao;
import com.liushao.user.pojo.User;
import com.liushao.user.pojo.UserSummaryResponse;
import com.liushao.web.InvalidRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户关注/取关与粉丝、关注列表。
 */
@Service
public class UserFollowService {

    private final UserDao userDao;
    private final UserFollowDao userFollowDao;

    public UserFollowService(UserDao userDao, UserFollowDao userFollowDao) {
        this.userDao = userDao;
        this.userFollowDao = userFollowDao;
    }

    /**
     * 关注：先登记唯一关系，再对被关注者的粉丝数、关注者的关注数做数据库端原子加 1。
     */
    @Transactional(timeout = 15)
    public FollowResult follow(String followerId, String followeeId) {
        if (followerId.equals(followeeId)) {
            return FollowResult.SELF;
        }
        if (userDao.findByIdForUpdate(followeeId).isEmpty()) {
            return FollowResult.NOT_FOUND;
        }
        if (userFollowDao.insertIfAbsent(followerId, followeeId) != 1) {
            return FollowResult.DUPLICATE;
        }
        userDao.incrementFans(followeeId);
        userDao.incrementFollow(followerId);
        return FollowResult.SUCCESS;
    }

    /**
     * 取关：删除唯一关系后对被关注者粉丝数、关注者关注数做原子减 1（不小于 0）。
     */
    @Transactional(timeout = 15)
    public FollowResult unfollow(String followerId, String followeeId) {
        if (userDao.findByIdForUpdate(followeeId).isEmpty()) {
            return FollowResult.NOT_FOUND;
        }
        if (userFollowDao.deleteRelation(followerId, followeeId) != 1) {
            return FollowResult.NOT_FOLLOWING;
        }
        userDao.decrementFans(followeeId);
        userDao.decrementFollow(followerId);
        return FollowResult.SUCCESS;
    }

    public PageResult<UserSummaryResponse> fans(String userId, int page, int size) {
        return list(userId, page, size, true);
    }

    public PageResult<UserSummaryResponse> follows(String userId, int page, int size) {
        return list(userId, page, size, false);
    }

    private PageResult<UserSummaryResponse> list(String userId, int page, int size, boolean fans) {
        if (page < 1 || size < 1 || size > 100) {
            throw new InvalidRequestException();
        }
        if (!userDao.existsById(userId)) {
            return null;
        }
        Page<User> users = fans
                ? userDao.findFollowers(userId, PageRequest.of(page - 1, size))
                : userDao.findFollowees(userId, PageRequest.of(page - 1, size));
        return new PageResult<>(users.getTotalElements(),
                users.getContent().stream().map(UserSummaryResponse::from).toList());
    }

    public enum FollowResult {
        SUCCESS, SELF, DUPLICATE, NOT_FOLLOWING, NOT_FOUND
    }
}
