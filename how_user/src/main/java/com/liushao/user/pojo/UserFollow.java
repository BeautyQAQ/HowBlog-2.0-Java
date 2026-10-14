package com.liushao.user.pojo;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 * 用户关注唯一关系；followerId 关注 followeeId。
 */
@Entity
@Table(name = "tb_user_follow")
public class UserFollow {
    @EmbeddedId
    private UserFollowId id;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;

    protected UserFollow() {
    }

    public UserFollow(UserFollowId id) {
        this.id = id;
    }

    public UserFollowId getId() {
        return id;
    }

    public Date getCreatedAt() {
        return createdAt;
    }
}
