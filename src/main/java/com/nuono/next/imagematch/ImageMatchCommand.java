package com.nuono.next.imagematch;

import org.springframework.web.multipart.MultipartFile;

public class ImageMatchCommand {

    private String originalImageUrl;
    private String candidateImageUrl;
    private ImageInput originalUpload;
    private ImageInput candidateUpload;
    private MultipartFile originalImageFile;
    private MultipartFile candidateImageFile;

    public String getOriginalImageUrl() {
        return originalImageUrl;
    }

    public void setOriginalImageUrl(String originalImageUrl) {
        this.originalImageUrl = originalImageUrl;
    }

    public String getCandidateImageUrl() {
        return candidateImageUrl;
    }

    public void setCandidateImageUrl(String candidateImageUrl) {
        this.candidateImageUrl = candidateImageUrl;
    }

    public ImageInput getOriginalUpload() {
        return originalUpload;
    }

    public void setOriginalUpload(String fileName, String contentType, byte[] bytes) {
        this.originalUpload = new ImageInput(fileName, contentType, bytes);
    }

    public ImageInput getCandidateUpload() {
        return candidateUpload;
    }

    public void setCandidateUpload(String fileName, String contentType, byte[] bytes) {
        this.candidateUpload = new ImageInput(fileName, contentType, bytes);
    }

    public MultipartFile getOriginalImageFile() {
        return originalImageFile;
    }

    public void setOriginalImageFile(MultipartFile originalImageFile) {
        this.originalImageFile = originalImageFile;
    }

    public MultipartFile getCandidateImageFile() {
        return candidateImageFile;
    }

    public void setCandidateImageFile(MultipartFile candidateImageFile) {
        this.candidateImageFile = candidateImageFile;
    }

    public static class ImageInput {
        private final String fileName;
        private final String contentType;
        private final byte[] bytes;

        public ImageInput(String fileName, String contentType, byte[] bytes) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        public String getFileName() {
            return fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public byte[] getBytes() {
            return bytes.clone();
        }
    }
}
