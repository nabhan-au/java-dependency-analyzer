package org.analyzer.models.json;

import org.analyzer.models.UseImportReport;

public class UseImportReportJson {
    public String importPath;
    public ImportArtifactJson artifact;
    public Boolean isDirectDependency;

    public UseImportReportJson(UseImportReport useImportReport) {
        this.importPath = useImportReport.importDetails.classPath.getOriginalPath().replace("\n", "").trim();
        this.artifact = new ImportArtifactJson(useImportReport.fromArtifact);
        this.isDirectDependency = useImportReport.isDirectDependency;
    }
}
