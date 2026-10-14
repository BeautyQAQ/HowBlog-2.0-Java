package com.liushao.user.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Date;
import javax.validation.constraints.Email;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 资料修改请求：只包含允许本人修改的字段；未提供的字段保留原值。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileUpdateRequest {
    @Pattern(regexp = "(?s).*\\S.*")
    @Size(max = 100)
    private String nickname;

    @Pattern(regexp = "(?s).*\\S.*")
    @Size(max = 500)
    private String avatar;

    @Pattern(regexp = "(?s).*\\S.*")
    @Size(max = 16)
    private String sex;

    private Date birthday;

    @Pattern(regexp = "(?s).*\\S.*")
    @Size(max = 255)
    @Email
    private String email;

    @Pattern(regexp = "(?s).*\\S.*")
    @Size(max = 500)
    private String interest;

    @Pattern(regexp = "(?s).*\\S.*")
    @Size(max = 500)
    private String personality;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public Date getBirthday() {
        return birthday;
    }

    public void setBirthday(Date birthday) {
        this.birthday = birthday;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getInterest() {
        return interest;
    }

    public void setInterest(String interest) {
        this.interest = interest;
    }

    public String getPersonality() {
        return personality;
    }

    public void setPersonality(String personality) {
        this.personality = personality;
    }
}
