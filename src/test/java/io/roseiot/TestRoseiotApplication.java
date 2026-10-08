package io.roseiot;

import org.springframework.boot.SpringApplication;

/**
 * 本地或 IDE 启动入口：PostgreSQL 由 {@link TestcontainersConfig} 提供，不依赖 Compose 与 {@code .env}。
 * 参考 spring-blog-api 的 {@code TestBlogApplication}。
 */
public final class TestRoseiotApplication {

    private TestRoseiotApplication() {}

    public static void main(String[] args) {
        System.setProperty("spring.docker.compose.enabled", "false");
        SpringApplication.from(RoseiotApplication::main).with(TestcontainersConfig.class).run(args);
    }
}
