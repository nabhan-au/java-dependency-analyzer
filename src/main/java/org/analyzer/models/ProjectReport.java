package org.analyzer.models;

public class ProjectReport {
    public String projectArtifact;
    public String repoPath;
    public String repoSubPath;
    public String artifactLocation;
    public FileImportReport[] fileImportReports;
    public ImportArtifact[] projectUsedDependencies;
    public ImportArtifact[] projectUseTransitiveDependencies;
    public ImportArtifact[] projectUnusedDependencies;

    public ProjectReport(String projectArtifact, String repoPath, String repoSubPath, String artifactLocation, FileImportReport[] fileImportReports, ImportArtifact[] projectUsedDependencies, ImportArtifact[] useTransitiveDependencies, ImportArtifact[] projectUnusedDependencies) {
        this.projectArtifact = projectArtifact;
        this.repoPath = repoPath;
        this.repoSubPath = repoSubPath;
        this.artifactLocation = artifactLocation;
        this.fileImportReports = fileImportReports;
        this.projectUsedDependencies = projectUsedDependencies;
        this.projectUnusedDependencies = projectUnusedDependencies;
        this.projectUseTransitiveDependencies = useTransitiveDependencies;
    }
}
