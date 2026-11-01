package com.wordonline.server.tutorial;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tutorial")
public record TutorialProperties(int maxBattles, int maxObjects, Duration lifetime) {
    public TutorialProperties {
        if (maxBattles == 0) maxBattles = 64;
        if (maxObjects == 0) maxObjects = 256;
        if (lifetime == null) lifetime = Duration.ofMinutes(20);
        if (maxBattles < 1 || maxObjects < 32 || lifetime.isNegative() || lifetime.isZero())
            throw new IllegalArgumentException("Invalid tutorial limits");
    }
}
