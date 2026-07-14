package com.nuono.next.intransit.autosync;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local-db")
@MapperScan(basePackages = "com.nuono.next.intransit.autosync", annotationClass = Mapper.class)
public class LogisticsAutoSyncMyBatisConfig {
}
