package io.roseiot.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import io.roseiot.shared.mybatis.RoseiotTenantLineHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {
        "io.roseiot.iam.resource",
        "io.roseiot.iam.role",
        "io.roseiot.iam.user",
        "io.roseiot.system",
        "io.roseiot.tenant"
}, markerInterface = BaseMapper.class)
public class MybatisPlusConfig {
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new RoseiotTenantLineHandler());

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(tenantInterceptor);
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        paginationInterceptor.setMaxLimit(100L);
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
