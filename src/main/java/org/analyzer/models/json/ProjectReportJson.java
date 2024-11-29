package org.analyzer.models.json;

import org.analyzer.models.ProjectReport;

import java.util.Arrays;

public class ProjectReportJson {
    public String projectArtifact;
    public String repositoryDir;
    public String repositorySubDir;
    public String artifactLocation;
    public FileImportReportJson[] fileImportReports;
    public ImportArtifactJson[] projectUseDependencies;
    public ImportArtifactJson[] projectUseTransitiveDependencies;
    public ImportArtifactJson[] projectUnusedDependencies;

    public ProjectReportJson(ProjectReport projectReport) {
        this.projectArtifact = projectReport.projectArtifact;
        this.repositoryDir = projectReport.repoPath;
        this.repositorySubDir = projectReport.repoSubPath;
        this.artifactLocation = projectReport.artifactLocation;
        this.fileImportReports = Arrays.stream(projectReport.fileImportReports).map(FileImportReportJson::new).toArray(FileImportReportJson[]::new);
        this.projectUseDependencies = Arrays.stream(projectReport.projectUsedDependencies).map(ImportArtifactJson::new).toArray(ImportArtifactJson[]::new);
        this.projectUseTransitiveDependencies = Arrays.stream(projectReport.projectUseTransitiveDependencies).map(ImportArtifactJson::new).toArray(ImportArtifactJson[]::new);
        this.projectUnusedDependencies = Arrays.stream(projectReport.projectUnusedDependencies).map(ImportArtifactJson::new).toArray(ImportArtifactJson[]::new);
    }
}
