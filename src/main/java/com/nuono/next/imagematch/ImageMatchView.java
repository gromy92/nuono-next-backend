package com.nuono.next.imagematch;

public class ImageMatchView {

    private Integer similarityScore;

    public ImageMatchView() {
    }

    public ImageMatchView(Integer similarityScore) {
        this.similarityScore = similarityScore;
    }

    public Integer getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Integer similarityScore) {
        this.similarityScore = similarityScore;
    }
}
