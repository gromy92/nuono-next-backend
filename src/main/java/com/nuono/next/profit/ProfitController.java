package com.nuono.next.profit;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profit")
public class ProfitController {

    private final ProfitCalculationService profitCalculationService;
    private final ProfitQuickSignalsService profitQuickSignalsService;

    public ProfitController(
            ProfitCalculationService profitCalculationService,
            ProfitQuickSignalsService profitQuickSignalsService
    ) {
        this.profitCalculationService = profitCalculationService;
        this.profitQuickSignalsService = profitQuickSignalsService;
    }

    @PostMapping("/calculate")
    public ProfitCalculationView calculate(@RequestBody ProfitCalculationCommand command) {
        try {
            return profitCalculationService.calculate(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/quick-signals")
    public ProfitQuickSignalsView quickSignals(@RequestBody ProfitQuickSignalsRequest request) {
        try {
            return profitQuickSignalsService.calculate(request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
