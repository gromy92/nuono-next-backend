package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-db")
public class LocalDbWarehouseDispatchService extends WarehousePackingOperations {

    public LocalDbWarehouseDispatchService(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

    @Transactional
    public List<PackingListView> confirmPackingLists(
            BusinessAccessContext access,
            List<String> packingListIds
    ) {
        LinkedHashSet<String> normalizedIds = new LinkedHashSet<>();
        if (packingListIds != null) {
            packingListIds.stream().map(id -> id == null ? "" : id.trim())
                    .filter(id -> !id.isEmpty()).forEach(normalizedIds::add);
        }
        if (normalizedIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个装箱单。");
        }
        List<PackingListView> views = new ArrayList<>();
        for (String packingListId : normalizedIds) {
            views.add(confirmPackingList(access, packingListId));
        }
        return views;
    }
}
