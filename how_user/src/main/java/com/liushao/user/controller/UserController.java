package com.liushao.user.controller;

import com.liushao.entity.Result;
import com.liushao.entity.StatusCode;
import com.liushao.user.pojo.LoginRequest;
import com.liushao.user.pojo.LoginResponse;
import com.liushao.user.service.UserService;
import com.liushao.user.pojo.RefreshRequest;
import com.liushao.auth.RefreshCredentialEndpoint;
import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("user")
@CrossOrigin
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @RequestMapping(value = "login", method = RequestMethod.POST)
    public Result login(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        LoginResponse result = userService.login(loginRequest);

        if (result != null) {
            return new Result(true, StatusCode.OK, "登录成功", result);
        }

        return new Result(false, StatusCode.LOGINERROR, "手机号或密码错误");
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
}
