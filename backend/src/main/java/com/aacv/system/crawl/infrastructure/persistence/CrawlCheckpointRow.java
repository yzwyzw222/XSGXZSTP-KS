package com.aacv.system.crawl.infrastructure.persistence;

public class CrawlCheckpointRow {
    private String cursorValue;
    private int committedPages;
    private long committedRecords;
    private long version;

    public String getCursorValue() { return cursorValue; }
    public void setCursorValue(String cursorValue) { this.cursorValue = cursorValue; }
    public int getCommittedPages() { return committedPages; }
    public void setCommittedPages(int committedPages) { this.committedPages = committedPages; }
    public long getCommittedRecords() { return committedRecords; }
    public void setCommittedRecords(long committedRecords) { this.committedRecords = committedRecords; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
