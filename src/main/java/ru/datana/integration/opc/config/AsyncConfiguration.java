package ru.datana.integration.opc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfiguration {

        @Bean(name = "subscriptionTaskExecutor")
        public TaskExecutor subscriptionTaskExecutor() {
                var executor = new ThreadPoolTaskExecutor();
                executor.setThreadNamePrefix("subscription-");
                executor.setCorePoolSize(2);
                executor.setMaxPoolSize(8);
                executor.setQueueCapacity(100);
                executor.initialize();
                return executor;
        }

        @Bean(name = "controllerUpdateTaskExecutor")
        public TaskExecutor controllerUpdateTaskExecutor() {
                var executor = new ThreadPoolTaskExecutor();
                executor.setThreadNamePrefix("controller-update-");
                executor.setCorePoolSize(4);
                executor.setMaxPoolSize(16);
                executor.setQueueCapacity(200);
                executor.initialize();
                return executor;
        }
}
