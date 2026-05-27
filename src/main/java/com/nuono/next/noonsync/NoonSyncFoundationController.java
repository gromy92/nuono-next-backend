package com.nuono.next.noonsync;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/noon-sync-foundation")
public class NoonSyncFoundationController {

    private final NoonSyncFoundationService service;

    public NoonSyncFoundationController(NoonSyncFoundationService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planCatalog", service.planCatalog());
        payload.put("plans", service.listPlans());
        payload.put("tasks", service.listTasks());
        return payload;
    }
}
