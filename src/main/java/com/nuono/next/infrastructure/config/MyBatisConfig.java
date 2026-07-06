package com.nuono.next.infrastructure.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local-db")
@MapperScan(basePackages = {
        "com.nuono.next.infrastructure.mapper",
        "com.nuono.next.intransit.autosync",
        "com.nuono.next.permission.access"
})
public class MyBatisConfig {
}
