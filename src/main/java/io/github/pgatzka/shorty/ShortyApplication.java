package io.github.pgatzka.shorty;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point of the Shorty application. */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class ShortyApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortyApplication.class, args);
    }

    /** Shared clock, replaced in tests. */
    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
