package org.example;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

// Guards against the app failing open to committed development secrets: a deployment that forgets
// SPRING_PROFILES_ACTIVE must not boot with a JWT key or admin password from the repository.
// Reads the main resources directly, because src/test/resources/application.properties shadows them.
class ConfigurationSecretsTest {

    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");

    @Test
    void thereIsNoDefaultProfile() throws IOException {
        assertThat(load("application.properties")).doesNotContainKey("spring.profiles.default");
    }

    @Test
    void committedPropertiesHaveNoSecretDefaults() throws IOException {
        for (String file : new String[] {"application.properties", "application-dev.properties"}) {
            Properties properties = load(file);
            assertThat(properties.getProperty("app.jwt.secret", ""))
                    .as("app.jwt.secret in %s", file).isIn("", "${JWT_SECRET:}");
            assertThat(properties.getProperty("app.admin.password", ""))
                    .as("app.admin.password in %s", file).isIn("", "${ADMIN_PASSWORD:}");
        }
    }

    private static Properties load(String file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(MAIN_RESOURCES.resolve(file))) {
            properties.load(reader);
        }
        return properties;
    }
}
