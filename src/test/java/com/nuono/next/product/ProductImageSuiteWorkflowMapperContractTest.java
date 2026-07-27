package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ProductImageSuiteWorkflowMapperContractTest {

    @Test
    void profileMapperShouldRegisterInheritedSingleVersionStatements() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(ProductImageProfileMapper.class);
        String namespace = ProductImageProfileMapper.class.getName();

        assertThat(configuration.hasStatement(namespace + ".selectLatestSuiteForUpdate")).isTrue();
        assertThat(configuration.hasStatement(namespace + ".restartSuiteGeneration")).isTrue();
        assertThat(configuration.hasStatement(namespace + ".restartSuiteForRework")).isTrue();

        Method select = ProductImageProfileMapper.class.getMethod("selectLatestSuiteForUpdate", Long.class);
        String selectSql = String.join(" ", select.getAnnotation(Select.class).value());
        assertThat(selectSql)
                .contains("suite_status <> 'DISCARDED'")
                .contains("LIMIT 1 FOR UPDATE");
    }

    @Test
    void restartStatementsShouldKeepTheSameSuiteAndAssetRows() throws Exception {
        Method restart = ProductImageProfileMapper.class.getMethod(
                "restartSuiteGeneration",
                Long.class, Long.class, String.class, Long.class, String.class,
                String.class, String.class, String.class, Long.class
        );
        String restartSql = String.join(" ", restart.getAnnotation(Update.class).value());
        assertThat(restartSql)
                .contains("suite_name = #{suiteName}")
                .contains("suite_status = 'PENDING_GENERATION'")
                .doesNotContain("INSERT INTO product_image_suite");

        Method replace = ProductImageProfileMapper.class.getMethod(
                "updateSuiteAssetContent",
                Long.class, Long.class, String.class, String.class, Long.class, String.class
        );
        String replaceSql = String.join(" ", replace.getAnnotation(Update.class).value());
        assertThat(replaceSql)
                .contains("UPDATE product_image_suite_asset")
                .contains("WHERE id = #{assetId} AND suite_id = #{suiteId}")
                .doesNotContain("INSERT INTO product_image_suite_asset");
    }
}
