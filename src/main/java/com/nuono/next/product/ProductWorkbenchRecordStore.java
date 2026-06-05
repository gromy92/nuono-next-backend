package com.nuono.next.product;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductWorkbenchRecordStore {

    private final ConcurrentMap<String, ProductWorkbenchRecord> records = new ConcurrentHashMap<>();

    ProductWorkbenchRecord get(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        return records.get(key);
    }

    void put(String key, ProductWorkbenchRecord record) {
        if (!StringUtils.hasText(key) || record == null) {
            return;
        }
        records.put(key, record);
    }
}
