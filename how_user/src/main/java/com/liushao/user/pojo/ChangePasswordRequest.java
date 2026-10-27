package com.liushao.user.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotBlank;

import com.liushao.user.validation.ValidPassword;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangePasswordRequest {
    @NotBlank
    private String oldPassword;

    @ValidPassword
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
