package org.analyzer.models.json;

import org.analyzer.models.UnusedImportReport;
import org.analyzer.models.UseImportReport;

public class UnusedImportReportJson {
    public String importPath;
    public ImportArtifactJson artifact;
    public Boolean isTransitive;

    public UnusedImportReportJson(UnusedImportReport unusedImportReport) {
        this.importPath = unusedImportReport.importDeclaration.toString().replace("\n", "").trim();
        this.artifact = new ImportArtifactJson(unusedImportReport.fromArtifact);
        this.isTransitive = unusedImportReport.isTransitive;
    }
}
