package com.dc.medapp.medappbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MedAppBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedAppBeApplication.class, args);
    }

}
