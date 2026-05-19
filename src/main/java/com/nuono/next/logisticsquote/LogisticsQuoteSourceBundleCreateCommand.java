package com.nuono.next.logisticsquote;

import java.util.ArrayList;
import java.util.List;

public class LogisticsQuoteSourceBundleCreateCommand {

    private String forwarderName;

    private String forwarderAlias;

    private String companyName;

    private String forwarderNotes;

    private String bundleName;

    private String analysisStatus;

    private String analysisSummary;

    private List<SourceFileInput> files = new ArrayList<>();

    private List<SourceNoteInput> notes = new ArrayList<>();

    public String getForwarderName() {
        return forwarderName;
    }

    public void setForwarderName(String forwarderName) {
        this.forwarderName = forwarderName;
    }

    public String getForwarderAlias() {
        return forwarderAlias;
    }

    public void setForwarderAlias(String forwarderAlias) {
        this.forwarderAlias = forwarderAlias;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getForwarderNotes() {
        return forwarderNotes;
    }

    public void setForwarderNotes(String forwarderNotes) {
        this.forwarderNotes = forwarderNotes;
    }

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public String getAnalysisStatus() {
        return analysisStatus;
    }

    public void setAnalysisStatus(String analysisStatus) {
        this.analysisStatus = analysisStatus;
    }

    public String getAnalysisSummary() {
        return analysisSummary;
    }

    public void setAnalysisSummary(String analysisSummary) {
        this.analysisSummary = analysisSummary;
    }

    public List<SourceFileInput> getFiles() {
        return files;
    }

    public void setFiles(List<SourceFileInput> files) {
        this.files = files == null ? new ArrayList<>() : files;
    }

    public List<SourceNoteInput> getNotes() {
        return notes;
    }

    public void setNotes(List<SourceNoteInput> notes) {
        this.notes = notes == null ? new ArrayList<>() : notes;
    }

    public static class SourceFileInput {

        private String fileName;

        private String fileType;

        private String filePath;

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }

    public static class SourceNoteInput {

        private String noteType;

        private String sourceChannel;

        private String content;

        private String authorName;

        public String getNoteType() {
            return noteType;
        }

        public void setNoteType(String noteType) {
            this.noteType = noteType;
        }

        public String getSourceChannel() {
            return sourceChannel;
        }

        public void setSourceChannel(String sourceChannel) {
            this.sourceChannel = sourceChannel;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getAuthorName() {
            return authorName;
        }

        public void setAuthorName(String authorName) {
            this.authorName = authorName;
        }
    }
}
