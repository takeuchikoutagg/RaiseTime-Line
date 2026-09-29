package com.example.raisetime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling：期限切れのリフレッシュトークンを定期的に削除するため
@SpringBootApplication
@EnableScheduling
public class RaisetimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaisetimeApplication.class, args);
    }

}
