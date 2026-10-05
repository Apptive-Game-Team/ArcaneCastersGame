package com.wordonline.server.playground;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "playground")
public record PlaygroundProperties(boolean enabled) {}
