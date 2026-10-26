package com.wordonline.server.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalInitTest {

    private static final String TOKEN = "0123456789abcdef".repeat(4);

    @TempDir
    Path directory;

    @Test
    void parsesTheInitLine() throws IOException {
        Path staticData = Files.writeString(directory.resolve("static.json"), "{}");

        LocalInit init = LocalInit.parse(line(TOKEN, directory.toString(), staticData.toString()));

        assertThat(init.token()).isEqualTo(TOKEN);
        assertThat(init.logDirectory()).isEqualTo(directory.resolve("logs"));
        assertThat(init.staticDataPath()).isEqualTo(staticData);
        assertThat(init.toString()).doesNotContain(TOKEN);
    }

    @Test
    void refusesAShortOrNonHexToken() throws IOException {
        Path staticData = Files.writeString(directory.resolve("static.json"), "{}");

        assertThatThrownBy(() -> LocalInit.parse(line("abcd", directory.toString(), staticData.toString())))
                .isInstanceOf(LocalStartupException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> LocalInit.parse(line("z".repeat(64), directory.toString(), staticData.toString())))
                .isInstanceOf(LocalStartupException.class).hasMessageContaining("32 bytes");
    }

    @Test
    void refusesRelativePathsAndMissingFiles() {
        assertThatThrownBy(() -> LocalInit.parse(line(TOKEN, "relative", directory.resolve("x").toString())))
                .isInstanceOf(LocalStartupException.class).hasMessageContaining("dataDir must be an absolute path");
        assertThatThrownBy(() -> LocalInit.parse(line(TOKEN, directory.toString(), directory.resolve("x").toString())))
                .isInstanceOf(LocalStartupException.class).hasMessageContaining("staticDataPath does not exist");
    }

    @Test
    void refusesAnythingButAnInitLine() {
        assertThatThrownBy(() -> LocalInit.parse(null)).hasMessageContaining("stdin closed");
        assertThatThrownBy(() -> LocalInit.parse("hello")).hasMessageContaining("not JSON");
        assertThatThrownBy(() -> LocalInit.parse("{\"type\":\"shutdown\"}")).hasMessageContaining("type\":\"init");
    }

    @Test
    void launcherTakesOverOnlyForTheLocalProfile() {
        assertThat(LocalLauncher.isRequested(new String[] {"--spring.profiles.active=local"})).isTrue();
        assertThat(LocalLauncher.isRequested(new String[] {"--spring.profiles.active=dev, local"})).isTrue();
        assertThat(LocalLauncher.isRequested(new String[] {"--spring.profiles.active=localhost"})).isFalse();
        assertThat(LocalLauncher.isRequested(new String[] {})).isFalse();
    }

    private static String line(String token, String dataDir, String staticDataPath) {
        return "{\"type\":\"init\",\"token\":\"" + token + "\",\"dataDir\":\"" + dataDir.replace("\\", "\\\\")
                + "\",\"staticDataPath\":\"" + staticDataPath.replace("\\", "\\\\") + "\"}";
    }
}
