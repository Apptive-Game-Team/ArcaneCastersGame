package com.wordonline.server.playground;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "playground")
public record PlaygroundProperties(@DefaultValue("true") boolean enabled) {}
