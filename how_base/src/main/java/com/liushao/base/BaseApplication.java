package com.liushao.base;

import com.liushao.auth.JwtConfiguration;
import com.liushao.util.IdWorker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 基础模块的启动类
 */
@SpringBootApplication
@Import(JwtConfiguration.class)
public class BaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(BaseApplication.class);
    }

    //定义IdWorker的Bean
    @Bean
    public IdWorker idWorker(){
        return new IdWorker(1,1);
    }

}