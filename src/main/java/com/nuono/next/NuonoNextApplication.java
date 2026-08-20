package com.nuono.next;

import com.nuono.next.procurement.aliorder.Ali1688Dp10OpenApiProbeCommand;
import com.nuono.next.datapull.cutover.DataPullRuntimeCutoverManifestCommand;
import com.nuono.next.noonpull.NoonReportDownloadProbeSourceCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class NuonoNextApplication {

    public static void main(String[] args) {
        if (DataPullRuntimeCutoverManifestCommand.handles(args)) {
            System.exit(DataPullRuntimeCutoverManifestCommand.run(args));
            return;
        }
        if (Ali1688Dp10OpenApiProbeCommand.handles(args)) {
            System.exit(Ali1688Dp10OpenApiProbeCommand.run(args));
            return;
        }
        if (NoonReportDownloadProbeSourceCommand.handles(args)) {
            System.exit(NoonReportDownloadProbeSourceCommand.run(args));
            return;
        }
        SpringApplication.run(NuonoNextApplication.class, args);
    }
}
