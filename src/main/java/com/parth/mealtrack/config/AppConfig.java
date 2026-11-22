package com.parth.mealtrack.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MealtrackProperties.class)
public class AppConfig implements WebMvcConfigurer {
    private final MealtrackProperties props;

    public AppConfig(MealtrackProperties props) {
        this.props = props;
    }

    /** All business dates are in India Standard Time. */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Kolkata"));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.corsOrigin())
                .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
                .allowedHeaders("Content-Type", "X-API-Key");
    }
}
