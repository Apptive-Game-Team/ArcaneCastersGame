package com.wordonline.server.playground;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "playground.enabled", havingValue = "true", matchIfMissing = true)
public class PlaygroundConfiguration {
    @Bean("playgroundClock")
    public Clock playgroundClock() {
        return Clock.systemUTC();
    }
}
