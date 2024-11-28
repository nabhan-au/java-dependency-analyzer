package org.analyzer.models.json;

import org.analyzer.models.FullPathCallingReport;

public class FullPathCallingReportJson {
    public String importPath;
    public ImportArtifactJson artifact;
    public Boolean isDirectDependency;

    public FullPathCallingReportJson(FullPathCallingReport report) {
        this.importPath = report.importClassPath.getOriginalPath();
        this.artifact = new ImportArtifactJson(report.fromArtifact);
        this.isDirectDependency = report.isDirectDependency;
    }
}
