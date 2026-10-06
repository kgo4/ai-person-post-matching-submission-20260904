package com.example.matching.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.example.matching.infrastructure.persistence.GraphRebuildTableRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 * <p>
 * 注册分页拦截器、乐观锁拦截器和动态表名拦截器。
 * 动态表名仅用于知识图谱全量重建期间将写操作路由到影子表，实现非破坏性重建。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 动态表名：知识图谱全量重建时 kg_graph_node/edge 路由到影子表
        DynamicTableNameInnerInterceptor dynamicTableName = new DynamicTableNameInnerInterceptor();
        dynamicTableName.setTableNameHandler((sql, tableName) -> GraphRebuildTableRouter.targetFor(tableName));
        interceptor.addInnerInterceptor(dynamicTableName);

        // 分页插件：IPage 分页查询时自动执行 COUNT 查询和 LIMIT 子句
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());

        // 乐观锁插件：配合 @Version 注解，更新时自动先对 version 做校验
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }
}
