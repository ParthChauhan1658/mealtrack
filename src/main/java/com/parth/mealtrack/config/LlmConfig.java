package com.parth.mealtrack.config;

import com.parth.mealtrack.llm.ClaudeClient;
import com.parth.mealtrack.llm.GeminiClient;
import com.parth.mealtrack.llm.LlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the provider from mealtrack.llm.provider. With "none" no LlmClient exists and rules do all the work. */
@Configuration
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(name = "mealtrack.llm.provider", havingValue = "claude")
    LlmClient claudeClient(MealtrackProperties props) {
        return new ClaudeClient(props.llm());
    }

    @Bean
    @ConditionalOnProperty(name = "mealtrack.llm.provider", havingValue = "gemini")
    LlmClient geminiClient(MealtrackProperties props) {
        return new GeminiClient(props.llm());
    }
}
