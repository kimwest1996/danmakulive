package com.danmakulive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class DanmakuLiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(DanmakuLiveApplication.class, args);
    }
}
