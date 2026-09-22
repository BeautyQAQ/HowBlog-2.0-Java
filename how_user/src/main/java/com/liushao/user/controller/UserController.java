package com.liushao.user.controller;

import com.liushao.entity.Result;
import com.liushao.entity.StatusCode;
import com.liushao.user.pojo.LoginRequest;
import com.liushao.user.pojo.LoginResponse;
import com.liushao.user.service.UserService;
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
    public Result login(@RequestBody LoginRequest loginRequest) {
        LoginResponse result = userService.login(loginRequest);

        if (result != null) {
            return new Result(true, StatusCode.OK, "登录成功", result);
        }

        return new Result(false, StatusCode.LOGINERROR, "手机号或密码错误");
    }
}
