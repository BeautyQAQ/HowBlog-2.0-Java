package com.liushao.user;

import com.liushao.auth.JwtConfiguration;
import com.liushao.web.ApiExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * @author huangshen
 */
@SpringBootApplication
@Import({JwtConfiguration.class, ApiExceptionHandler.class})
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
