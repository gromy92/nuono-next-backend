package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

class FileParseUploadArchiveSpringWiringTest {

    @Test
    void localDbFacadeUsesManagedUploadArchiveModule() {
        new ApplicationContextRunner(() -> {
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.getEnvironment().setActiveProfiles("local-db");
            return context;
        })
                .withBean(FileManagementParseMapper.class, () -> mock(FileManagementParseMapper.class))
                .withBean(FileParseStorageProperties.class, FileParseStorageProperties::new)
                .withBean(FileParseInputExtractionService.class, () -> mock(FileParseInputExtractionService.class))
                .withBean(FileParseStructuredAiService.class, () -> mock(FileParseStructuredAiService.class))
                .withBean(FileParseResultDiffService.class, () -> mock(FileParseResultDiffService.class))
                .withBean(FileParseResultPersistenceService.class, () -> mock(FileParseResultPersistenceService.class))
                .withBean(FileParseItemReviewService.class, () -> mock(FileParseItemReviewService.class))
                .withBean(FileParseQueryViewService.class, () -> mock(FileParseQueryViewService.class))
                .withBean(FileParsePublishService.class, () -> mock(FileParsePublishService.class))
                .withBean(
                        FileParseLogisticsChannelActivationService.class,
                        () -> mock(FileParseLogisticsChannelActivationService.class)
                )
                .withUserConfiguration(WiringConfig.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    FileParseUploadArchiveService archiveService = context.getBean(FileParseUploadArchiveService.class);
                    LocalDbFileManagementParseService facade = context.getBean(LocalDbFileManagementParseService.class);
                    assertNotNull(archiveService);
                    assertSame(archiveService, ReflectionTestUtils.getField(facade, "uploadArchiveService"));
                });
    }

    @Configuration
    @Import({FileParseUploadArchiveService.class, LocalDbFileManagementParseService.class})
    static class WiringConfig {
    }
}
