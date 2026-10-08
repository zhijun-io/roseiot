package io.roseiot.identity.infra.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("io.roseiot.identity.persistence")
public class MybatisPlusConfig {
}
