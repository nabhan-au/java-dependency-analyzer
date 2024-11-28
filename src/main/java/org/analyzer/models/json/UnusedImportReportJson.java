package org.analyzer.models.json;

import org.analyzer.models.UnusedImportReport;

public class UnusedImportReportJson {
    public String importPath;
    public ImportArtifactJson artifact;
    public Boolean isDirectDependency;

    public UnusedImportReportJson(UnusedImportReport unusedImportReport) {
        this.importPath = unusedImportReport.importDeclaration.toString().replace("\n", "").trim();
        this.artifact = new ImportArtifactJson(unusedImportReport.fromArtifact);
        this.isDirectDependency = unusedImportReport.isDirectDependency;
    }
}
