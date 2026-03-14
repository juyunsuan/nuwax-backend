package com.xspaceagi.custompage.domain.threadpool;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户页面项目的线程池配置
 */
@Configuration
public class CustomPageAsyncConfig {

    /**
     * 用于处理聊天流式响应的线程池
     */
    @Bean("aiChatFluxExecutor")
    public Executor aiChatFluxExecutor() {
        return new ThreadPoolExecutor(
                20, // 核心线程数
                100, // 最大线程数
                60L, // 线程空闲时间
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500), // 任务队列
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    private final String namePrefix = "ai-chat-flux-";

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
                        t.setDaemon(true); // 设置为守护线程
                        return t;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy() // 拒绝策略：抛出异常
        );
    }

    // 用于异步调用 AI Chat 的线程池
    // 对于AI调用这种IO密集型任务，线程在执行期间大部分时间在等待网络响应
    // 
    // 配置说明：
    // - 并发执行能力：最多可同时执行 100 个任务（最大线程数）
    // - 队列缓冲能力：最多可排队等待 500 个任务
    // - 总接受能力：最多可同时接纳 600 个任务（执行中 + 排队）
    // 
    // - 如果平均AI响应时间为10秒，那么每10秒内100个线程可以完成100个任务
    // - 如果用户平均每100秒发一次请求，理论上可以支持约 1000 个活跃用户
    // - 如果用户持续快速发请求，可能只能支持约 100 个用户
    @Bean("aiChatCallExecutor")
    public Executor aiChatCallExecutor() {
        return new ThreadPoolExecutor(
            20, // 核心线程数
            100, // 最大线程数
            60L, // 线程空闲时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500), // 任务队列
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                private final String namePrefix = "ai-chat-call-";

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
                    t.setDaemon(true); // 设置为守护线程
                    return t;
                }
            },
            new ThreadPoolExecutor.AbortPolicy() // 拒绝策略：抛出异常（避免阻塞调用者线程）
        );
    }
}
