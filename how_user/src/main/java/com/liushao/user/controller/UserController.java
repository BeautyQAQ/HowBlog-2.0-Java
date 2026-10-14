package com.liushao.user.controller;

import com.liushao.auth.AuthenticatedUser;
import com.liushao.auth.CurrentUserContext;
import com.liushao.auth.RequireAuthentication;
import com.liushao.entity.PageResult;
import com.liushao.entity.Result;
import com.liushao.entity.StatusCode;
import com.liushao.user.pojo.ChangePasswordRequest;
import com.liushao.user.pojo.LoginRequest;
import com.liushao.user.pojo.LoginResponse;
import com.liushao.user.pojo.ProfileResponse;
import com.liushao.user.pojo.ProfileUpdateRequest;
import com.liushao.user.pojo.RegisterRequest;
import com.liushao.user.pojo.UserSummaryResponse;
import com.liushao.user.service.UserService;
import com.liushao.user.service.UserFollowService;
import com.liushao.user.service.AuthRateLimiter;
import com.liushao.user.pojo.RefreshRequest;
import com.liushao.auth.RefreshCredentialEndpoint;
import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("user")
public class UserController {

    private final UserService userService;
    private final UserFollowService userFollowService;
    private final AuthRateLimiter rateLimiter;

    public UserController(UserService userService, UserFollowService userFollowService, AuthRateLimiter rateLimiter) {
        this.userService = userService;
        this.userFollowService = userFollowService;
        this.rateLimiter = rateLimiter;
    }

    @RequestMapping(value = "login", method = RequestMethod.POST)
    public Result login(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (loginRequest.getMobile() != null && !loginRequest.getMobile().isBlank()) {
            rateLimiter.check(AuthRateLimiter.Scope.LOGIN_ACCOUNT, loginRequest.getMobile());
        }
        LoginResponse result = userService.login(loginRequest);

        if (result != null) {
            return new Result(true, StatusCode.OK, "登录成功", result);
        }

        return new Result(false, StatusCode.LOGINERROR, "手机号或密码错误");
    }

    @PostMapping("register")
    public ResponseEntity<Result> register(@Valid @RequestBody RegisterRequest request) {
        UserService.RegisterResult result = userService.register(request);
        return switch (result) {
            case SUCCESS -> ResponseEntity.ok(new Result(true, StatusCode.OK, "注册成功"));
            case DUPLICATE -> ResponseEntity.ok(new Result(false, StatusCode.REPERROR, "手机号已注册"));
            case INVALID -> ResponseEntity.badRequest()
                    .body(new Result(false, StatusCode.ERROR, "请求格式或参数不正确"));
        };
    }

    @GetMapping("profile")
    @RequireAuthentication
    public ResponseEntity<Result> profile() {
        ProfileResponse profile = userService.profile(CurrentUserContext.require().getUserId());
        return profile == null
                ? ResponseEntity.status(401).body(new Result(false, StatusCode.ACCESSERROR, "请先登录"))
                : ResponseEntity.ok(new Result(true, StatusCode.OK, "查询成功", profile));
    }

    @PutMapping("profile")
    @RequireAuthentication
    public ResponseEntity<Result> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        ProfileResponse profile = userService.updateProfile(CurrentUserContext.require().getUserId(), request);
        return profile == null
                ? ResponseEntity.status(401).body(new Result(false, StatusCode.ACCESSERROR, "请先登录"))
                : ResponseEntity.ok(new Result(true, StatusCode.OK, "修改成功", profile));
    }

    @PostMapping("password")
    @RequireAuthentication
    public ResponseEntity<Result> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        AuthenticatedUser user = CurrentUserContext.require();
        UserService.PasswordChangeResult result =
                userService.changePassword(user.getUserId(), user.getSessionId(), request);
        if (result == null) {
            return ResponseEntity.status(401).body(new Result(false, StatusCode.ACCESSERROR, "请先登录"));
        }
        return switch (result) {
            case SUCCESS -> ResponseEntity.ok(new Result(true, StatusCode.OK, "修改成功"));
            case WRONG_PASSWORD -> ResponseEntity.ok(new Result(false, StatusCode.LOGINERROR, "旧密码不正确"));
            case INVALID -> ResponseEntity.badRequest()
                    .body(new Result(false, StatusCode.ERROR, "请求格式或参数不正确"));
        };
    }

    @PutMapping("follow/{userId}")
    @RequireAuthentication
    public ResponseEntity<Result> follow(@PathVariable String userId) {
        UserFollowService.FollowResult result =
                userFollowService.follow(CurrentUserContext.require().getUserId(), userId);
        return followResponse(result, "关注成功");
    }

    @DeleteMapping("follow/{userId}")
    @RequireAuthentication
    public ResponseEntity<Result> unfollow(@PathVariable String userId) {
        UserFollowService.FollowResult result =
                userFollowService.unfollow(CurrentUserContext.require().getUserId(), userId);
        return followResponse(result, "已取消关注");
    }

    @GetMapping("{userId}/fans/{page}/{size}")
    public ResponseEntity<Result> fans(@PathVariable String userId, @PathVariable int page, @PathVariable int size) {
        return listResponse(userFollowService.fans(userId, page, size));
    }

    @GetMapping("{userId}/follows/{page}/{size}")
    public ResponseEntity<Result> follows(@PathVariable String userId, @PathVariable int page, @PathVariable int size) {
        return listResponse(userFollowService.follows(userId, page, size));
    }

    @PostMapping("refresh")
    @RefreshCredentialEndpoint
    public ResponseEntity<Result> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse result = userService.refresh(request.getRefreshToken());
        return ResponseEntity.status(result == null ? 401 : 200).header("Cache-Control", "no-store")
                .body(result == null ? new Result(false, StatusCode.ACCESSERROR, "请重新登录")
                        : new Result(true, StatusCode.OK, "刷新成功", result));
    }

    @PostMapping("logout")
    @RefreshCredentialEndpoint
    public ResponseEntity<Result> logout(@Valid @RequestBody RefreshRequest request) {
        boolean revoked = userService.logout(request.getRefreshToken());
        return ResponseEntity.status(revoked ? 200 : 401).header("Cache-Control", "no-store")
                .body(revoked ? new Result(true, StatusCode.OK, "退出成功")
                        : new Result(false, StatusCode.ACCESSERROR, "请重新登录"));
    }

    private ResponseEntity<Result> followResponse(UserFollowService.FollowResult result, String successMessage) {
        return switch (result) {
            case SUCCESS -> ResponseEntity.ok(new Result(true, StatusCode.OK, successMessage));
            case SELF -> ResponseEntity.ok(new Result(false, StatusCode.ERROR, "不能关注自己"));
            case DUPLICATE -> ResponseEntity.ok(new Result(false, StatusCode.REPERROR, "已关注"));
            case NOT_FOLLOWING -> ResponseEntity.ok(new Result(false, StatusCode.REPERROR, "未关注"));
            case NOT_FOUND -> ResponseEntity.status(404).body(new Result(false, StatusCode.ERROR, "资源不存在"));
        };
    }

    private ResponseEntity<Result> listResponse(PageResult<UserSummaryResponse> page) {
        return page == null
                ? ResponseEntity.status(404).body(new Result(false, StatusCode.ERROR, "资源不存在"))
                : ResponseEntity.ok(new Result(true, StatusCode.OK, "查询成功", page));
    }
}
