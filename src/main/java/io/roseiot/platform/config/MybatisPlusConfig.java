package io.roseiot.platform.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("io.roseiot.platform.persistence")
public class MybatisPlusConfig {
}
