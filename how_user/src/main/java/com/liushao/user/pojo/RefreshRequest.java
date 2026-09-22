package com.liushao.user.pojo;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class RefreshRequest {
    @NotBlank
    @Pattern(regexp = "[0-9a-f]{64}")
    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}