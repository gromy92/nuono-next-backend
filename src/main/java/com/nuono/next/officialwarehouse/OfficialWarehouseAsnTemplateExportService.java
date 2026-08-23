package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseAsnTemplateProvider.PullRequest;
import com.nuono.next.officialwarehouse.OfficialWarehouseAsnTemplateProvider.TemplateFile;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
@ConditionalOnBean(OfficialWarehouseAsnTemplateProvider.class)
public class OfficialWarehouseAsnTemplateExportService {

    private final OfficialWarehouseAsnTemplateProvider provider;
    private final OfficialWarehouseAsnTemplateCsv csv;

    public OfficialWarehouseAsnTemplateExportService(
            OfficialWarehouseAsnTemplateProvider provider,
            OfficialWarehouseAsnTemplateCsv csv
    ) {
        this.provider = provider;
        this.csv = csv;
    }

    public ExportFile export(BusinessAccessContext access, OfficialWarehouseAsnTemplateExportCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少约仓模板导出参数。");
        }
        String storeCode = requireText(command.storeCode, "请选择要导出约仓模板的店铺。");
        String siteCode = requireText(command.siteCode, "请选择要导出约仓模板的站点。").toUpperCase(Locale.ROOT);
        Map<String, Integer> quantities = csv.normalizeQuantities(command.lines);
        TemplateFile template;
        try {
            template = provider.fetchLatest(new PullRequest(
                    requireOwnerUserId(access, storeCode), storeCode, siteCode
            ));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("读取 Noon 最新约仓模板失败，请稍后重试。", exception);
        }
        byte[] content = csv.fillQuantities(template.content, quantities, siteCode);
        return new ExportFile(content, filledFileName(template.fileName));
    }

    private Long requireOwnerUserId(BusinessAccessContext access, String storeCode) {
        if (access == null) {
            throw new IllegalArgumentException("缺少业务访问上下文。");
        }
        Long ownerUserId = access.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = access.getBusinessOwnerUserId();
        }
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("无法识别当前业务老板账号。");
        }
        return ownerUserId;
    }

    private String filledFileName(String fileName) {
        String safeName = StringUtils.hasText(fileName) ? fileName.trim() : "fbn_eligible_items.csv";
        int extension = safeName.toLowerCase(Locale.ROOT).lastIndexOf(".csv");
        return extension < 0
                ? safeName + "_filled.csv"
                : safeName.substring(0, extension) + "_filled.csv";
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public static class ExportFile {
        public final byte[] content;
        public final String fileName;

        public ExportFile(byte[] content, String fileName) {
            this.content = content;
            this.fileName = fileName;
        }
    }
}
