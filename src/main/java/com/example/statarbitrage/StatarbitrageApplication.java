package com.example.statarbitrage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class StatarbitrageApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatarbitrageApplication.class, args);
    }

}
