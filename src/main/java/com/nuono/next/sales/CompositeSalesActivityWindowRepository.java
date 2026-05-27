package com.nuono.next.sales;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Primary
@Repository
public class CompositeSalesActivityWindowRepository implements SalesActivityWindowRepository {

    private final SalesActivityWindowRepository legacyRepository;
    private final List<SalesActivityWindowCompatibilitySource> compatibilitySources;

    @Autowired
    public CompositeSalesActivityWindowRepository(
            MyBatisSalesActivityWindowRepository legacyRepository,
            List<SalesActivityWindowCompatibilitySource> compatibilitySources
    ) {
        this((SalesActivityWindowRepository) legacyRepository, compatibilitySources);
    }

    CompositeSalesActivityWindowRepository(
            SalesActivityWindowRepository legacyRepository,
            List<SalesActivityWindowCompatibilitySource> compatibilitySources
    ) {
        this.legacyRepository = legacyRepository;
        this.compatibilitySources = compatibilitySources == null ? List.of() : compatibilitySources;
    }

    @Override
    public SalesActivityWindowRecord save(SalesActivityWindowRecord record) {
        return legacyRepository.save(record);
    }

    @Override
    public SalesActivityWindowRecord find(Long id) {
        return legacyRepository.find(id);
    }

    @Override
    public void setEnabled(Long id, boolean enabled, Long updatedBy) {
        legacyRepository.setEnabled(id, enabled, updatedBy);
    }

    @Override
    public List<SalesActivityWindowRecord> listHistory(SalesActivityWindowScope scope) {
        return legacyRepository.listHistory(scope);
    }

    @Override
    public List<SalesActivityWindowRecord> listActive(SalesActivityWindowScope scope) {
        List<SalesActivityWindowRecord> combined = new ArrayList<>();
        List<SalesActivityWindowRecord> legacy = legacyRepository.listActive(scope);
        if (legacy != null) {
            combined.addAll(legacy);
        }
        for (SalesActivityWindowCompatibilitySource source : compatibilitySources) {
            List<SalesActivityWindowRecord> compatible = source.listActive(scope);
            if (compatible != null) {
                combined.addAll(compatible);
            }
        }
        return combined;
    }
}
