package com.nuono.next.imagematch;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/image-match")
public class ImageMatchController {

    private final ImageMatchService service;

    public ImageMatchController(ImageMatchService service) {
        this.service = service;
    }

    @PostMapping(value = "/compare", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ImageMatchView compareJson(@RequestBody(required = false) ImageMatchRequest request) {
        ImageMatchCommand command = new ImageMatchCommand();
        if (request != null) {
            command.setOriginalImageUrl(request.getOriginalImageUrl());
            command.setCandidateImageUrl(request.getCandidateImageUrl());
        }
        return service.compare(command);
    }

    @PostMapping(value = "/compare", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageMatchView compareMultipart(
            @RequestParam(value = "originalImageUrl", required = false) String originalImageUrl,
            @RequestParam(value = "candidateImageUrl", required = false) String candidateImageUrl,
            @RequestParam(value = "originalImageFile", required = false) MultipartFile originalImageFile,
            @RequestParam(value = "candidateImageFile", required = false) MultipartFile candidateImageFile
    ) {
        ImageMatchCommand command = new ImageMatchCommand();
        command.setOriginalImageUrl(originalImageUrl);
        command.setCandidateImageUrl(candidateImageUrl);
        command.setOriginalImageFile(originalImageFile);
        command.setCandidateImageFile(candidateImageFile);
        return service.compare(command);
    }

    @ExceptionHandler(ImageMatchException.class)
    public ResponseEntity<Map<String, String>> imageMatchException(ImageMatchException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(Map.of("message", exception.getMessage()));
    }
}
