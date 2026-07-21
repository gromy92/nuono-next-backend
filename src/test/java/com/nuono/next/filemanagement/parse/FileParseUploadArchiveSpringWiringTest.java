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
    void localDbFacadeUsesManagedFileParseModules() {
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
                    FileParseActionPolicy actionPolicy = context.getBean(FileParseActionPolicy.class);
                    FileParseUploadArchiveService archiveService = context.getBean(FileParseUploadArchiveService.class);
                    FileParseTaskCatalogService catalogService = context.getBean(FileParseTaskCatalogService.class);
                    FileParseTaskCreationService creationService = context.getBean(FileParseTaskCreationService.class);
                    LocalDbFileManagementParseService facade = context.getBean(LocalDbFileManagementParseService.class);
                    assertNotNull(actionPolicy);
                    assertNotNull(archiveService);
                    assertNotNull(catalogService);
                    assertNotNull(creationService);
                    assertSame(actionPolicy, ReflectionTestUtils.getField(facade, "actionPolicy"));
                    assertSame(archiveService, ReflectionTestUtils.getField(facade, "uploadArchiveService"));
                    assertSame(catalogService, ReflectionTestUtils.getField(facade, "taskCatalogService"));
                    assertSame(creationService, ReflectionTestUtils.getField(facade, "taskCreationService"));
                    assertSame(actionPolicy, ReflectionTestUtils.getField(catalogService, "actionPolicy"));
                    assertSame(archiveService, ReflectionTestUtils.getField(catalogService, "uploadArchiveService"));
                    assertSame(actionPolicy, ReflectionTestUtils.getField(creationService, "actionPolicy"));
                    assertSame(archiveService, ReflectionTestUtils.getField(creationService, "uploadArchiveService"));
                });
    }

    @Configuration
    @Import({
            FileParseActionPolicy.class,
            FileParseUploadArchiveService.class,
            FileParseTaskCatalogService.class,
            FileParseTaskCreationService.class,
            LocalDbFileManagementParseService.class
    })
    static class WiringConfig {
    }
}
