package com.liushao.user.pojo;

import java.util.Date;

/**
 * 用户资料的安全视图：包含可对外字段，不包含密码。
 */
public class ProfileResponse {
    private String id;
    private String mobile;
    private String nickname;
    private String sex;
    private String avatar;
    private String email;
    private Date birthday;
    private Date regdate;
    private Date updatedate;
    private Date lastdate;
    private Long online;
    private String interest;
    private String personality;
    private Integer fanscount;
    private Integer followcount;

    private ProfileResponse() {
    }

    public static ProfileResponse from(User user) {
        ProfileResponse response = new ProfileResponse();
        response.id = user.getId();
        response.mobile = user.getMobile();
        response.nickname = user.getNickname();
        response.sex = user.getSex();
        response.avatar = user.getAvatar();
        response.email = user.getEmail();
        response.birthday = user.getBirthday();
        response.regdate = user.getRegdate();
        response.updatedate = user.getUpdatedate();
        response.lastdate = user.getLastdate();
        response.online = user.getOnline();
        response.interest = user.getInterest();
        response.personality = user.getPersonality();
        response.fanscount = user.getFanscount();
        response.followcount = user.getFollowcount();
        return response;
    }

    public String getId() {
        return id;
    }

    public String getMobile() {
        return mobile;
    }

    public String getNickname() {
        return nickname;
    }

    public String getSex() {
        return sex;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getEmail() {
        return email;
    }

    public Date getBirthday() {
        return birthday;
    }

    public Date getRegdate() {
        return regdate;
    }

    public Date getUpdatedate() {
        return updatedate;
    }

    public Date getLastdate() {
        return lastdate;
    }

    public Long getOnline() {
        return online;
    }

    public String getInterest() {
        return interest;
    }

    public String getPersonality() {
        return personality;
    }

    public Integer getFanscount() {
        return fanscount;
    }

    public Integer getFollowcount() {
        return followcount;
    }
}
