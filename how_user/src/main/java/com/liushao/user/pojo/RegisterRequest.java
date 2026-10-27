package com.liushao.user.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.liushao.user.validation.ValidPassword;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterRequest {
    @NotBlank
    @Pattern(regexp = "1\\d{10}")
    private String mobile;

    @ValidPassword
    private String password;

    @Size(max = 100)
    private String nickname;

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
