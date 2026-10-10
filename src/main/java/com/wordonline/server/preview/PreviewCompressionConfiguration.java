package com.wordonline.server.preview;

import org.springframework.boot.web.server.Compression;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
class PreviewCompressionConfiguration {
    @Bean WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> previewJsonCompression() {
        return factory -> {
            Compression compression = new Compression();
            compression.setEnabled(true);
            compression.setMimeTypes(new String[]{"application/json"});
            compression.setMinResponseSize(DataSize.ofKilobytes(1));
            factory.setCompression(compression);
        };
    }
}
