package com.aacv.system.ingestion.infrastructure.persistence;

class CanonicalEntityRow {

    private Long id;
    private String openAlexId;
    private String displayName;
    private String issn;
    private String countryCode;
    private String type;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOpenAlexId() { return openAlexId; }
    public void setOpenAlexId(String openAlexId) { this.openAlexId = openAlexId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getIssn() { return issn; }
    public void setIssn(String issn) { this.issn = issn; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
