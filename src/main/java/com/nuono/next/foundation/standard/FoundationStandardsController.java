package com.nuono.next.foundation.standard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/foundation-standards")
public class FoundationStandardsController {

    private final FoundationStandardsService standardsService;

    public FoundationStandardsController(FoundationStandardsService standardsService) {
        this.standardsService = standardsService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facts", standardsService.listFactDefinitions());
        payload.put("qualityStates", standardsService.listQualityStates());
        payload.put("metrics", standardsService.listMetricDefinitions());
        payload.put("aiOutputModes", standardsService.listAiOutputModes());
        payload.put("adapterStandard", standardsService.adapterStandard());
        payload.put("workflowStandard", standardsService.workflowStandard());
        return payload;
    }

    @GetMapping("/facts")
    public List<FoundationFactDefinition> facts() {
        return standardsService.listFactDefinitions();
    }

    @GetMapping("/metrics")
    public List<FoundationMetricDefinition> metrics() {
        return standardsService.listMetricDefinitions();
    }

    @GetMapping("/ai-output-modes")
    public List<FoundationAiOutputModeDefinition> aiOutputModes() {
        return standardsService.listAiOutputModes();
    }

    @GetMapping("/adapter-standard")
    public FoundationAdapterStandard adapterStandard() {
        return standardsService.adapterStandard();
    }

    @GetMapping("/workflow-standard")
    public FoundationWorkflowStandard workflowStandard() {
        return standardsService.workflowStandard();
    }
}
