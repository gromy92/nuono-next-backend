package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class FileParseReviewPublicationControllerTest extends FileParseHttpTestFixture {

    @Test
    void delegatesVersionAndActivationQueries() {
        FileParseVersionListView versions = new FileParseVersionListView();
        FileParseVersionItemsView items = new FileParseVersionItemsView();
        FileParseLogisticsChannelActivationView activations = new FileParseLogisticsChannelActivationView();
        when(service.listVersions(session, 4005L, 1, 20)).thenReturn(versions);
        when(service.listVersionItems(session, 70005L, 2, 1000)).thenReturn(items);
        when(service.listLogisticsChannelActivations(session, 4005L, 70005L)).thenReturn(activations);
        FileParseReviewPublicationController controller = controller();

        assertEquals(versions, controller.versions(4005L, 1, 20, request));
        assertEquals(items, controller.versionItems(70005L, 2, 1000, request));
        assertEquals(activations, controller.logisticsChannelActivations(4005L, 70005L, request));
    }

    @Test
    void delegatesActivationBatchAndPublishCommands() {
        FileParseLogisticsChannelActivationCommand activationCommand =
                new FileParseLogisticsChannelActivationCommand();
        FileParseLogisticsChannelActivationView activations = new FileParseLogisticsChannelActivationView();
        FileParseBatchReviewCommand batchCommand = new FileParseBatchReviewCommand();
        FileParseBatchReviewView batch = new FileParseBatchReviewView();
        FileParsePublishCommand publishCommand = new FileParsePublishCommand();
        FileParsePublishView publication = new FileParsePublishView();
        when(service.saveLogisticsChannelActivations(session, activationCommand)).thenReturn(activations);
        when(service.batchAcceptResultItems(session, 20L, batchCommand, "batch-key")).thenReturn(batch);
        when(service.publishTask(session, 20L, publishCommand, "publish-key")).thenReturn(publication);
        FileParseReviewPublicationController controller = controller();

        assertEquals(activations, controller.saveLogisticsChannelActivations(activationCommand, request));
        assertEquals(batch, controller.batchAcceptItems(20L, batchCommand, "batch-key", request));
        assertEquals(publication, controller.publishTask(20L, publishCommand, "publish-key", request));
    }

    @Test
    void delegatesEveryReviewActionWithExactServiceVocabulary() {
        FileParseReviewCommand command = new FileParseReviewCommand();
        FileParseProcessingItemView result = new FileParseProcessingItemView();
        when(service.reviewResultItem(session, 20L, 50L, "edit", command, "edit-key")).thenReturn(result);
        when(service.reviewResultItem(session, 20L, 50L, "accept", command, "accept-key")).thenReturn(result);
        when(service.reviewResultItem(session, 20L, 50L, "reject", command, "reject-key")).thenReturn(result);
        when(service.reviewResultItem(session, 20L, 50L, "keep_old", command, "keep-key")).thenReturn(result);
        FileParseReviewPublicationController controller = controller();

        assertEquals(result, controller.editItem(20L, 50L, command, "edit-key", request));
        assertEquals(result, controller.acceptItem(20L, 50L, command, "accept-key", request));
        assertEquals(result, controller.rejectItem(20L, 50L, command, "reject-key", request));
        assertEquals(result, controller.keepOldItem(20L, 50L, command, "keep-key", request));
        verify(service).reviewResultItem(session, 20L, 50L, "keep_old", command, "keep-key");
    }

    private FileParseReviewPublicationController controller() {
        return new FileParseReviewPublicationController(support);
    }
}
