package com.liushao.user.pojo;

/**
 * 用户公开摘要视图：用于粉丝/关注列表，只包含可对外字段，不含手机号、邮箱、生日与密码。
 */
public class UserSummaryResponse {
    private String id;
    private String nickname;
    private String avatar;
    private String sex;
    private String interest;
    private String personality;
    private Integer fanscount;
    private Integer followcount;

    private UserSummaryResponse() {
    }

    public static UserSummaryResponse from(User user) {
        UserSummaryResponse response = new UserSummaryResponse();
        response.id = user.getId();
        response.nickname = user.getNickname();
        response.avatar = user.getAvatar();
        response.sex = user.getSex();
        response.interest = user.getInterest();
        response.personality = user.getPersonality();
        response.fanscount = user.getFanscount();
        response.followcount = user.getFollowcount();
        return response;
    }

    public String getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getSex() {
        return sex;
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
