package com.nuono.next.imagematch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ImageMatchControllerTest {

    @Mock
    private ImageMatchService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new ImageMatchController(service))
                .build();
    }

    @Test
    void jsonCompareEndpointReturnsSimilarityScoreOnly() throws Exception {
        when(service.compare(any(ImageMatchCommand.class))).thenReturn(new ImageMatchView(87));

        mvc.perform(post("/api/image-match/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalImageUrl\":\"https://img.example/original.jpg\",\"candidateImageUrl\":\"https://img.example/candidate.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similarityScore").value(87))
                .andExpect(jsonPath("$.matchLevel").doesNotExist())
                .andExpect(jsonPath("$.reasons").doesNotExist());

        ArgumentCaptor<ImageMatchCommand> captor = ArgumentCaptor.forClass(ImageMatchCommand.class);
        verify(service).compare(captor.capture());
        ImageMatchCommand command = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("https://img.example/original.jpg", command.getOriginalImageUrl());
        org.junit.jupiter.api.Assertions.assertEquals("https://img.example/candidate.jpg", command.getCandidateImageUrl());
    }

    @Test
    void multipartCompareEndpointReturnsSimilarityScoreOnly() throws Exception {
        when(service.compare(any(ImageMatchCommand.class))).thenReturn(new ImageMatchView(91));

        mvc.perform(multipart("/api/image-match/compare")
                        .file(new MockMultipartFile("originalImageFile", "original.png", "image/png", new byte[] {1}))
                        .file(new MockMultipartFile("candidateImageFile", "candidate.png", "image/png", new byte[] {2})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similarityScore").value(91))
                .andExpect(jsonPath("$.warnings").doesNotExist());

        ArgumentCaptor<ImageMatchCommand> captor = ArgumentCaptor.forClass(ImageMatchCommand.class);
        verify(service).compare(captor.capture());
        ImageMatchCommand command = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("original.png", command.getOriginalImageFile().getOriginalFilename());
        org.junit.jupiter.api.Assertions.assertEquals("candidate.png", command.getCandidateImageFile().getOriginalFilename());
    }

    @Test
    void imageMatchExceptionMapsToItsHttpStatus() throws Exception {
        when(service.compare(any(ImageMatchCommand.class)))
                .thenThrow(new ImageMatchException(HttpStatus.BAD_GATEWAY, "AI failed"));

        mvc.perform(post("/api/image-match/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalImageUrl\":\"https://img.example/original.jpg\",\"candidateImageUrl\":\"https://img.example/candidate.jpg\"}"))
                .andExpect(status().isBadGateway());
    }
}
