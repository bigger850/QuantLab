package com.quantlab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ExecutorConfig {

    /**
     * 行情同步专用线程池。
     * 并发度刻意压低，避免触发上游限流；队列满时由调用线程执行，形成天然背压。
     */
    @Bean("syncExecutor")
    public ThreadPoolTaskExecutor syncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("sync-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 回测计算线程池。回测是 CPU 密集，线程数不超过核数。
     */
    @Bean("backtestExecutor")
    public ThreadPoolTaskExecutor backtestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(Math.max(2, cores / 2));
        executor.setMaxPoolSize(Math.max(4, cores));
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("backtest-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
