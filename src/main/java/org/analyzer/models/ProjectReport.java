package org.analyzer.models;

public class ProjectReport {
    public String projectArtifact;
    private String repoPath;
    private String repoSubPath;
    private String artifactLocation;
    public FileImportReport[] fileImportReports;
    public ImportArtifact[] projectUsedDependencies;
    public ImportArtifact[] projectUnusedDependencies;

    public ProjectReport(String projectArtifact, String repoPath, String repoSubPath, String artifactLocation, FileImportReport[] fileImportReports, ImportArtifact[] projectUsedDependencies, ImportArtifact[] projectUnusedDependencies) {
        this.projectArtifact = projectArtifact;
        this.repoPath = repoPath;
        this.repoSubPath = repoSubPath;
        this.artifactLocation = artifactLocation;
        this.fileImportReports = fileImportReports;
        this.projectUsedDependencies = projectUsedDependencies;
        this.projectUnusedDependencies = projectUnusedDependencies;
    }
}
