package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.infrastructure.mapper.NoonDataPullScopeMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonDataPullScopeSourceTest {

    @Test
    void mapsEveryBoundScopeWithoutDependingOnCurrentAuthorizationState() {
        NoonDataPullScopeRow first = row(307L, 108065L, "PRJ108065", "STR108065-NSA", "SA");
        NoonDataPullScopeRow second = row(307L, 108065L, "PRJ108065", "STR108065-NEN", "AE");
        NoonDataPullScopeMapper mapper = mapper(List.of(first, second));
        NoonDataPullScopeSource source = new NoonDataPullScopeSource(mapper);

        List<DataPullScope> scopes = source.listScopes();

        assertEquals(2, scopes.size());
        assertEquals(307L, scopes.get(0).getOwnerUserId());
        assertEquals(108065L, scopes.get(0).getLogicalStoreId());
        assertEquals("PRJ108065", scopes.get(0).getAccountKey());
        assertNull(scopes.get(0).getEgressKey());
        assertNotEquals(scopes.get(0).getStableScopeKey(), scopes.get(1).getStableScopeKey());
    }

    @Test
    void invalidActiveSourceFailsClosed() {
        NoonDataPullScopeRow row = row(307L, 108065L, "PRJ108065", "STR108065-NSA", "SA");
        NoonDataPullScopeRow invalid = row(307L, 108065L, "PRJ108065", "", "SA");
        NoonDataPullScopeSource source = new NoonDataPullScopeSource(
                mapper(List.of(row, invalid))
        );

        assertThrows(IllegalArgumentException.class, source::listScopes);
    }

    @Test
    void duplicateActiveSourceFailsClosedInsteadOfChoosingOnePhysicalRow() {
        NoonDataPullScopeRow first = row(
                307L, 108065L, "PRJ108065", "STR108065-NSA", "SA"
        );
        NoonDataPullScopeRow duplicate = row(
                307L, 108065L, "PRJ108065", "STR108065-NSA", "SA"
        );
        duplicate.setUserStoreId(first.getUserStoreId() + 1L);
        NoonDataPullScopeSource source = new NoonDataPullScopeSource(
                mapper(List.of(first, duplicate))
        );

        assertThrows(IllegalStateException.class, source::listScopes);
    }

    private NoonDataPullScopeMapper mapper(List<NoonDataPullScopeRow> rows) {
        return new NoonDataPullScopeMapper() {
            @Override
            public List<NoonDataPullScopeRow> listActiveBoundScopes() {
                return rows;
            }

            @Override
            public List<NoonDataPullScopeRow> listActiveBoundScopesAfter(
                    Long afterOwnerUserId,
                    Long afterLogicalStoreId,
                    Long afterLogicalStoreSiteId,
                    Long afterUserProjectId,
                    Long afterUserStoreId,
                    int limit
            ) {
                return rows.subList(0, Math.min(rows.size(), limit));
            }
        };
    }

    private NoonDataPullScopeRow row(
            long owner,
            long logicalStore,
            String project,
            String store,
            String site
    ) {
        NoonDataPullScopeRow row = new NoonDataPullScopeRow();
        row.setOwnerUserId(owner);
        row.setLogicalStoreId(logicalStore);
        row.setLogicalStoreSiteId(logicalStore * 10L + 1L);
        row.setUserProjectId(logicalStore * 10L + 2L);
        row.setUserStoreId(logicalStore * 10L + 3L);
        row.setProjectCode(project);
        row.setStoreCode(store);
        row.setSiteCode(site);
        return row;
    }
}
