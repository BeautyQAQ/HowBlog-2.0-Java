package com.liushao.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.entity.PageResult;
import com.liushao.user.dao.UserDao;
import com.liushao.user.dao.UserFollowDao;
import com.liushao.user.pojo.User;
import com.liushao.user.pojo.UserSummaryResponse;
import com.liushao.web.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFollowServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private UserDao userDao;
    @Mock private UserFollowDao userFollowDao;

    private UserFollowService service() {
        return new UserFollowService(userDao, userFollowDao);
    }

    @Test
    void followRegistersRelationAndIncrementsBothCounters() {
        when(userDao.findByIdForUpdate("bob")).thenReturn(Optional.of(new User()));
        when(userFollowDao.insertIfAbsent("alice", "bob")).thenReturn(1);

        assertEquals(UserFollowService.FollowResult.SUCCESS, service().follow("alice", "bob"));

        verify(userDao).incrementFans("bob");
        verify(userDao).incrementFollow("alice");
    }

    @Test
    void followRejectsSelfDuplicateAndMissing() {
        assertEquals(UserFollowService.FollowResult.SELF, service().follow("alice", "alice"));

        when(userDao.findByIdForUpdate("bob")).thenReturn(Optional.of(new User()));
        when(userFollowDao.insertIfAbsent("alice", "bob")).thenReturn(0);
        assertEquals(UserFollowService.FollowResult.DUPLICATE, service().follow("alice", "bob"));

        when(userDao.findByIdForUpdate("ghost")).thenReturn(Optional.empty());
        assertEquals(UserFollowService.FollowResult.NOT_FOUND, service().follow("alice", "ghost"));

        verify(userDao, never()).incrementFans(any());
        verify(userDao, never()).incrementFollow(any());
    }

    @Test
    void unfollowDeletesRelationAndDecrementsBothCounters() {
        when(userDao.findByIdForUpdate("bob")).thenReturn(Optional.of(new User()));
        when(userFollowDao.deleteRelation("alice", "bob")).thenReturn(1);

        assertEquals(UserFollowService.FollowResult.SUCCESS, service().unfollow("alice", "bob"));

        verify(userDao).decrementFans("bob");
        verify(userDao).decrementFollow("alice");
    }

    @Test
    void unfollowRejectsMissingAndNotFollowing() {
        when(userDao.findByIdForUpdate("bob")).thenReturn(Optional.of(new User()));
        when(userFollowDao.deleteRelation("alice", "bob")).thenReturn(0);
        assertEquals(UserFollowService.FollowResult.NOT_FOLLOWING, service().unfollow("alice", "bob"));

        when(userDao.findByIdForUpdate("ghost")).thenReturn(Optional.empty());
        assertEquals(UserFollowService.FollowResult.NOT_FOUND, service().unfollow("alice", "ghost"));

        verify(userDao, never()).decrementFans(any());
        verify(userDao, never()).decrementFollow(any());
    }

    @Test
    void listsFollowersWithPublicFieldsOnly() throws Exception {
        when(userDao.existsById("alice")).thenReturn(true);
        when(userDao.findFollowers("alice", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(summaryUser("bob", "Bob"))));

        PageResult<UserSummaryResponse> page = service().fans("alice", 1, 10);

        assertEquals(1L, page.getTotal());
        assertEquals("bob", page.getRows().get(0).getId());
        assertEquals("Bob", page.getRows().get(0).getNickname());
        String json = objectMapper.writeValueAsString(page);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("mobile"));
    }

    @Test
    void listsFolloweesWithPublicFieldsOnly() {
        when(userDao.existsById("alice")).thenReturn(true);
        when(userDao.findFollowees("alice", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(summaryUser("carol", "Carol"))));

        PageResult<UserSummaryResponse> page = service().follows("alice", 1, 10);

        assertEquals("carol", page.getRows().get(0).getId());
    }

    @Test
    void listsReturnNullForMissingUserAndRejectInvalidPaging() {
        assertThrows(InvalidRequestException.class, () -> service().fans("alice", 0, 10));
        assertThrows(InvalidRequestException.class, () -> service().follows("alice", 1, 101));
        assertThrows(InvalidRequestException.class, () -> service().fans("alice", 1, 0));

        when(userDao.existsById("ghost")).thenReturn(false);
        assertNull(service().fans("ghost", 1, 10));
    }

    private User summaryUser(String id, String nickname) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        user.setMobile("13800000000");
        user.setPassword("$2a$10$secret");
        user.setFanscount(3);
        user.setFollowcount(4);
        return user;
    }
}
