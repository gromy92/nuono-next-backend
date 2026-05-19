package com.nuono.next.logisticsquote;

public class LogisticsQuoteSourceBundleNoteUpdateCommand {

    private Long noteId;

    private String content;

    public Long getNoteId() {
        return noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
