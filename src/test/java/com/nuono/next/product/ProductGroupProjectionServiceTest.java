package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductGroupMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ProductGroupProjectionServiceTest {

    @Mock
    private ProductGroupMapper productGroupMapper;

    private ProductGroupProjectionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProductGroupProjectionService(productGroupMapper, new ObjectMapper());
    }

    @Test
    void confirmedUngroupedSnapshotClearsOldGroupProjection() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setGroup(Map.of("state", "当前商品未挂 group"));

        service.persistGroupProjection(50001L, 51001L, 52001L, snapshot, "2026-05-18 10:00:00", 10002L);

        verify(productGroupMapper).markActiveGroupMembersDeletedByProductMasterId(51001L, 10002L);
        verify(productGroupMapper).refreshProductGroupMemberCountsByProductMasterId(51001L, 10002L);
        verify(productGroupMapper).clearProductMasterGroupFieldsById(51001L, 10002L);
    }

    @Test
    void missingGroupFetchDoesNotClearOldGroupProjection() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setGroup(Map.of());

        service.persistGroupProjection(50001L, 51001L, 52001L, snapshot, "2026-05-18 10:00:00", 10002L);

        verify(productGroupMapper, never()).markActiveGroupMembersDeletedByProductMasterId(51001L, 10002L);
        verify(productGroupMapper, never()).refreshProductGroupMemberCountsByProductMasterId(51001L, 10002L);
        verify(productGroupMapper, never()).clearProductMasterGroupFieldsById(51001L, 10002L);
    }

    @Test
    void hydrateClearsStaleSnapshotGroupWhenCurrentProjectionIsUngrouped() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setGroup(Map.of("skuGroup", "OLD", "groupRef", "12in1"));

        service.hydrateSnapshotGroupFromCurrentProjection(
                10002L,
                "STR245027-NAE",
                "Z3065D60053B999AE0D32Z",
                snapshot,
                List.of()
        );

        assertEquals("当前商品未挂 group", snapshot.getGroup().get("state"));
        assertFalse(snapshot.getGroup().containsKey("skuGroup"));
    }

    @Test
    void hydrateRestoresSnapshotGroupFromCurrentProjectionMembers() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        ProductGroupProjectionRecord group = new ProductGroupProjectionRecord();
        group.setProductGroupId(60001L);
        group.setSkuGroup("Z15E8BF09FCCF78B8653CG");
        group.setGroupRef("12in1");
        group.setGroupRefCanonical("12IN1");
        group.setGroupName("12in1");
        group.setBrand("generic");
        group.setProductFulltype("home_decor-lighting-table_lamps");
        group.setAxesJson("[{\"axisCode\":\"colour_name\",\"axisName\":\"Colour Name\"}]");
        group.setConditionsJson("{\"brand\":\"generic\"}");
        group.setMemberCount(1);
        ProductGroupMemberProjectionRecord member = new ProductGroupMemberProjectionRecord();
        member.setSkuParent("Z2666F058EF551EB603A1Z");
        member.setAxisValuesJson("{\"colour_name\":\"white\",\"axisValues\":{\"colour_name\":\"white\"}}");
        member.setTitle("Galaxy Projector");
        member.setImageUrl("https://img.example.com/a.jpeg");
        when(productGroupMapper.selectCurrentProductGroupProjection(
                10002L,
                "STR245027-NAE",
                "Z2666F058EF551EB603A1Z"
        )).thenReturn(group);
        when(productGroupMapper.selectActiveProductGroupMembers(60001L)).thenReturn(List.of(member));

        service.hydrateSnapshotGroupFromCurrentProjection(
                10002L,
                "STR245027-NAE",
                "Z2666F058EF551EB603A1Z",
                snapshot,
                List.of()
        );

        assertEquals("Z15E8BF09FCCF78B8653CG", snapshot.getGroup().get("skuGroup"));
        assertEquals("12in1", snapshot.getGroup().get("groupRef"));
        assertEquals(1, snapshot.getGroup().get("memberCount"));
        List<Map<String, Object>> members = ProductGroupSnapshotSupport.recordListValue(snapshot.getGroup().get("members"));
        assertEquals("Z2666F058EF551EB603A1Z", members.get(0).get("skuParent"));
        assertEquals("white", members.get(0).get("colour_name"));
        assertEquals("Galaxy Projector", members.get(0).get("title"));
        assertEquals("https://img.example.com/a.jpeg", members.get(0).get("imageUrl"));
    }
}
