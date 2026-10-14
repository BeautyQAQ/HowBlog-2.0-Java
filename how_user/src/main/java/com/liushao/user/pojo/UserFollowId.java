package com.liushao.user.pojo;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class UserFollowId implements Serializable {
    @Column(name = "follower_id")
    private String followerId;

    @Column(name = "followee_id")
    private String followeeId;

    protected UserFollowId() {
    }

    public UserFollowId(String followerId, String followeeId) {
        this.followerId = followerId;
        this.followeeId = followeeId;
    }

    public String getFollowerId() {
        return followerId;
    }

    public String getFolloweeId() {
        return followeeId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof UserFollowId that)) return false;
        return Objects.equals(followerId, that.followerId) && Objects.equals(followeeId, that.followeeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(followerId, followeeId);
    }
}
