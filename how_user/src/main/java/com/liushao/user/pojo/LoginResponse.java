package com.liushao.user.pojo;

public class LoginResponse {
    private String avatar;
    private long expiresIn;
    private String id;
    private String mobile;
    private String nickname;
    private String token;
    private String tokenType;
    private String refreshToken;
    private long refreshExpiresIn;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public long getRefreshExpiresIn() { return refreshExpiresIn; }
    public void setRefreshExpiresIn(long refreshExpiresIn) { this.refreshExpiresIn = refreshExpiresIn; }

    private LoginResponse() {
    }

    public static LoginResponse from(User user, String token, long expiresIn) {
        LoginResponse response = new LoginResponse();
        response.setAvatar(user.getAvatar());
        response.setExpiresIn(expiresIn);
        response.setId(user.getId());
        response.setMobile(user.getMobile());
        response.setNickname(user.getNickname());
        response.setToken(token);
        response.setTokenType("Bearer");
        return response;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
}