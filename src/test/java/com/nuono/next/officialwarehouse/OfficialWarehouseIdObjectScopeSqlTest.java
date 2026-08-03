package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseObjectScopeSql;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class OfficialWarehouseIdObjectScopeSqlTest {

    @Test
    void idQueriesUseExactStoreOwnerPairsAndFailClosedForEmptyScope() throws Exception {
        assertProvider("selectAuthorizedAsn", "selectAuthorizedAsn");
        assertProvider("selectAuthorizedAppointment", "selectAuthorizedAppointment");

        String asnSql = OfficialWarehouseObjectScopeSql.selectAuthorizedAsn();
        String appointmentSql = OfficialWarehouseObjectScopeSql.selectAuthorizedAppointment();

        assertExactPairScope(asnSql, "official_warehouse_asn");
        assertExactPairScope(appointmentSql, "official_warehouse_appointment");
    }

    @Test
    void appointmentIdQueryRequiresAnExactParentAsnMatch() {
        assertParentMatch(OfficialWarehouseObjectScopeSql.selectAuthorizedAppointment());
        assertParentMatch(OfficialWarehouseObjectScopeSql.selectAppointmentByOwner());
        assertParentMatch(OfficialWarehouseObjectScopeSql.selectLatestAppointmentByAsn());
    }

    @Test
    void mybatisExpandsEachAuthorizedPairAndKeepsEmptyScopeValid() {
        Map<String, Long> pairs = new LinkedHashMap<>();
        pairs.put("STORE-A", 307L);
        pairs.put("STORE-B", 408L);

        String scopedSql = boundSql(
                OfficialWarehouseObjectScopeSql.selectAuthorizedAsn(),
                Map.of("asnId", 500001L, "storeOwnerUserIds", pairs)
        ).getSql().replaceAll("\\s+", " ");
        String emptySql = boundSql(
                OfficialWarehouseObjectScopeSql.selectAuthorizedAsn(),
                Map.of("asnId", 500001L, "storeOwnerUserIds", Map.of())
        ).getSql().replaceAll("\\s+", " ");

        assertThat(scopedSql)
                .contains("official_warehouse_asn.owner_user_id = ?")
                .contains("UPPER(official_warehouse_asn.store_code) = UPPER(?)")
                .contains(" OR ");
        assertThat(emptySql).contains("AND 1 = 0");
    }

    @Test
    void projectionsAndSchedulerRejectMismatchedChildRecords() throws Exception {
        String lines = selectSql("listAsnLines", Long.class);
        String links = selectSql("listAsnShippingBatchLinks", Long.class);
        String appointments = selectSql(
                "listAppointments",
                Long.class,
                Collection.class,
                String.class,
                String.class,
                String.class,
                String.class,
                int.class
        );
        String due = selectSql("listDueAppointments", int.class);

        assertThat(lines)
                .contains("parent_asn.owner_user_id = awl.owner_user_id")
                .contains("UPPER(parent_asn.store_code) = UPPER(awl.store_code)");
        assertThat(links)
                .contains("parent_line.id = link.asn_line_id")
                .contains("parent_asn.owner_user_id = link.owner_user_id");
        assertThat(appointments)
                .contains("parent_asn.id = official_warehouse_appointment.asn_id")
                .contains("UPPER(parent_asn.site_code) = UPPER(official_warehouse_appointment.site_code)");
        assertThat(due)
                .contains("parent_asn.id = official_warehouse_appointment.asn_id")
                .contains("parent_asn.owner_user_id = official_warehouse_appointment.owner_user_id");
    }

    private static void assertProvider(String mapperMethod, String providerMethod) throws Exception {
        Method method = OfficialWarehouseMapper.class.getMethod(mapperMethod, Map.class, Long.class);
        SelectProvider provider = method.getAnnotation(SelectProvider.class);
        assertThat(provider).isNotNull();
        assertThat(provider.type()).isEqualTo(OfficialWarehouseObjectScopeSql.class);
        assertThat(provider.method()).isEqualTo(providerMethod);
    }

    private static void assertExactPairScope(String sql, String table) {
        assertThat(sql)
                .contains("collection='storeOwnerUserIds'")
                .contains(table + ".owner_user_id = #{ownerUserId}")
                .contains("UPPER(" + table + ".store_code) = UPPER(#{storeCode})")
                .contains("<otherwise>")
                .contains("AND 1 = 0")
                .doesNotContain("owner_user_id IN")
                .doesNotContain("store_code IN");
    }

    private static String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Select select = OfficialWarehouseMapper.class.getMethod(methodName, parameterTypes).getAnnotation(Select.class);
        assertThat(select).isNotNull();
        return String.join("\n", select.value());
    }

    private static void assertParentMatch(String sql) {
        assertThat(sql)
                .contains("parent_asn.id = official_warehouse_appointment.asn_id")
                .contains("parent_asn.owner_user_id = official_warehouse_appointment.owner_user_id")
                .contains("UPPER(parent_asn.store_code) = UPPER(official_warehouse_appointment.store_code)")
                .contains("UPPER(parent_asn.site_code) = UPPER(official_warehouse_appointment.site_code)")
                .contains("parent_asn.is_deleted = b'0'");
    }

    private static BoundSql boundSql(String script, Map<String, Object> parameters) {
        Configuration configuration = new Configuration();
        SqlSource source = new XMLLanguageDriver().createSqlSource(configuration, script, Map.class);
        return source.getBoundSql(parameters);
    }
}
