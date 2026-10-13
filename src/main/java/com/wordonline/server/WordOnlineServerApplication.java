package com.wordonline.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.wordonline.server.local.LocalLauncher;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class WordOnlineServerApplication {

    public static void main(String[] args) {
        // A server the desktop client runs reads its init line from stdin before Spring starts.
        if (LocalLauncher.isRequested(args)) {
            LocalLauncher.launch(args);
            return;
        }
        SpringApplication.run(WordOnlineServerApplication.class, args);
    }

}
