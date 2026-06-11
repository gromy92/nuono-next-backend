package com.nuono.next.imagematch;

public class ImageMatchRequest {

    private String originalImageUrl;
    private String candidateImageUrl;

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
}
