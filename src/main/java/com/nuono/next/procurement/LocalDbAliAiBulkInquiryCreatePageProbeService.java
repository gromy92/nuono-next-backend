package com.nuono.next.procurement;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbAliAiBulkInquiryCreatePageProbeService {

    private final AliAiBulkInquiryCreatePageReadAdapter readAdapter;
    private final AliAiBulkInquiryCreatePageProbeParser parser;

    public LocalDbAliAiBulkInquiryCreatePageProbeService(
            AliAiBulkInquiryCreatePageReadAdapter readAdapter,
            AliAiBulkInquiryCreatePageProbeParser parser
    ) {
        this.readAdapter = readAdapter;
        this.parser = parser;
    }

    public AliAiBulkInquiryCreatePageProbeView probePage(AliAiBulkInquiryCreatePageProbeCommand command) {
        AliAiBulkInquiryCreatePageProbeCommand safeCommand = command == null
                ? new AliAiBulkInquiryCreatePageProbeCommand()
                : command;
        if (StringUtils.hasText(safeCommand.getSampleHtml())) {
            return parser.parseSampleHtml(safeCommand.getSampleHtml(), safeCommand.getPageUrl());
        }

        AliAiBulkInquiryCreatePageSnapshot snapshot = readAdapter.readCreatePage(
                safeCommand.getPageUrl(),
                Boolean.TRUE.equals(safeCommand.getOpenIfMissing())
        );
        return parser.parseSnapshot(snapshot, "chrome");
    }
}
