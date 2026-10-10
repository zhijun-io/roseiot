package io.roseiot.system.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("io.roseiot.system.persistence")
public class SystemMybatisConfig {}
