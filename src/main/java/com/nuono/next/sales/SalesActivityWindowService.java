package com.nuono.next.sales;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SalesActivityWindowService {

    private final SalesActivityWindowRepository repository;

    public SalesActivityWindowService(SalesActivityWindowRepository repository) {
        this.repository = repository;
    }

    public SalesActivityWindowRecord save(SalesActivityWindowCommand command) {
        int versionNo = 1;
        if (command.getId() != null) {
            SalesActivityWindowRecord previous = repository.find(command.getId());
            versionNo = previous.getVersionNo() + 1;
            repository.setEnabled(previous.getId(), false, command.getOperatorUserId());
        }
        return repository.save(new SalesActivityWindowRecord(
                null,
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getName(),
                command.getActivityType(),
                command.getCategoryScope(),
                command.getDateFrom(),
                command.getDateTo(),
                command.getFactor(),
                command.isEnabled(),
                versionNo,
                command.getOperatorUserId(),
                command.getOperatorUserId()
        ));
    }

    public SalesActivityWindowRecord find(Long id) {
        return repository.find(id);
    }

    public void setEnabled(Long id, boolean enabled, Long updatedBy) {
        repository.setEnabled(id, enabled, updatedBy);
    }

    public List<SalesActivityWindowRecord> history(SalesActivityWindowScope scope) {
        return repository.listHistory(scope);
    }

    public SalesActivityWindowSnapshot activeSnapshot(SalesActivityWindowScope scope) {
        return new SalesActivityWindowSnapshot(repository.listActive(scope));
    }
}
