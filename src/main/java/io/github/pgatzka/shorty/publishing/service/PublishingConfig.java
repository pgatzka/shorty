package io.github.pgatzka.shorty.publishing.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** A single render worker: renders and uploads run one at a time, in order. */
@Configuration(proxyBeanMethods = false)
class PublishingConfig {

    static final String RENDER_EXECUTOR = "renderExecutor";

    @Bean(name = RENDER_EXECUTOR)
    ThreadPoolTaskExecutor renderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("render-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
