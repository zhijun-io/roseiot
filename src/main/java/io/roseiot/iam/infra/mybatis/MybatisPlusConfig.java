package io.roseiot.iam.infra.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("io.roseiot.iam.persistence")
public class MybatisPlusConfig {
}
