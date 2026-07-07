package com.nuono.next.postsaleprofit;

import com.nuono.next.infrastructure.mapper.PostSaleProfitMapper;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.FxRateRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostSaleProfitFxRateService {
    private final PostSaleProfitMapper mapper;

    public PostSaleProfitFxRateService(PostSaleProfitMapper mapper) {
        this.mapper = mapper;
    }

    public List<PostSaleProfitFxRateView> listRates(Long ownerUserId, String siteCode) {
        return mapper.listFxRates(ownerUserId, siteCode).stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Transactional
    public PostSaleProfitFxRateView saveRate(PostSaleProfitFxRateCommand command) {
        validate(command);
        FxRateRow row = new FxRateRow();
        row.ownerUserId = command.getOwnerUserId();
        row.siteCode = command.getSiteCode();
        row.currency = command.getCurrency();
        row.rateToCny = command.getRateToCny();
        row.effectiveFrom = command.getEffectiveFrom();
        row.effectiveTo = command.getEffectiveTo();
        row.sourceLabel = command.getSourceLabel();
        mapper.softDeleteSameFxRate(row);
        mapper.insertFxRate(row);
        return toView(row);
    }

    private void validate(PostSaleProfitFxRateCommand command) {
        if (command.getRateToCny() == null || command.getRateToCny().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "汇率必须大于 0。");
        }
        if (command.getEffectiveFrom() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生效开始日期必填。");
        }
        if (command.getEffectiveTo() != null && command.getEffectiveTo().isBefore(command.getEffectiveFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生效结束日期不能早于开始日期。");
        }
        if (command.getCurrency() == null || command.getCurrency().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "币种必填。");
        }
    }

    private PostSaleProfitFxRateView toView(FxRateRow row) {
        return new PostSaleProfitFxRateView(
                row.id,
                row.siteCode,
                row.currency,
                row.rateToCny,
                row.effectiveFrom == null ? null : row.effectiveFrom.toString(),
                row.effectiveTo == null ? null : row.effectiveTo.toString(),
                row.sourceLabel
        );
    }
}
