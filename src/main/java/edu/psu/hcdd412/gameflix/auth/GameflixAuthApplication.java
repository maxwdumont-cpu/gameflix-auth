package edu.psu.hcdd412.gameflix.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GameflixAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameflixAuthApplication.class, args);
    }
}
