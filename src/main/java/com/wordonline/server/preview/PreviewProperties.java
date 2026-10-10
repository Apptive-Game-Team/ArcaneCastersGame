package com.wordonline.server.preview;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "preview")
public record PreviewProperties(int maxObjects, int maxTicks, int maxClipBytes) {
    public PreviewProperties {
        maxObjects = maxObjects > 0 ? maxObjects : 512;
        maxTicks = maxTicks > 0 ? maxTicks : 600;
        maxClipBytes = maxClipBytes > 0 ? maxClipBytes : 4 * 1024 * 1024;
    }
}
