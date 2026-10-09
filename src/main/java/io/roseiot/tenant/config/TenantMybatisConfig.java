package io.roseiot.tenant.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("io.roseiot.tenant.persistence")
public class TenantMybatisConfig {
}
