package com.nuono.next.officialwarehouse;

import com.nuono.next.datapull.report.FbnReportDownloadTransport;
import com.nuono.next.noon.NoonBinaryDownloadSink;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Real restart-safe streaming transport for DP-07-B report downloads. */
@Component
@Profile("local-db")
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
public final class OfficialWarehouseFbnReportDownloadTransport
        implements FbnReportDownloadTransport {
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;

    public OfficialWarehouseFbnReportDownloadTransport(
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory
    ) {
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public void download(
            OfficialWarehouseFbnExportProvider.PullRequest request,
            String downloadUrl,
            NoonBinaryDownloadSink sink
    ) {
        requireScope(request);
        if (!StringUtils.hasText(downloadUrl)) {
            throw new IllegalArgumentException("缺少官方仓 FBN 报表下载地址。");
        }
        NoonPullStoreBinding binding = bindingResolver.resolve(
                NoonInterfacePullRequest.builder()
                        .ownerUserId(request.ownerUserId)
                        .storeCode(request.storeCode)
                        .siteCode(request.siteCode)
                        .dataDomain(NoonPullDataDomain.PRODUCT)
                        .requestName("official-warehouse-fbn-export-download")
                        .targetIdentity(
                                "official-warehouse-fbn-export-download:" + request.storeCode
                        )
                        .build()
        );
        sessionFactory.openOneShot(binding).getBytesOnce(
                downloadUrl.trim(),
                false,
                Map.of("Accept", "text/csv,*/*", "Accept-Encoding", "identity"),
                sink
        );
    }

    private void requireScope(OfficialWarehouseFbnExportProvider.PullRequest request) {
        if (request == null || request.ownerUserId == null || request.ownerUserId <= 0L
                || !StringUtils.hasText(request.storeCode)
                || !StringUtils.hasText(request.siteCode)) {
            throw new IllegalArgumentException("官方仓 FBN 报表缺少 owner/store/site scope。");
        }
    }
}
