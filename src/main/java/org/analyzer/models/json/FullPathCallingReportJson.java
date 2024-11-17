package org.analyzer.models.json;

import org.analyzer.models.FullPathCallingReport;
import org.analyzer.models.ImportArtifact;
import org.analyzer.models.ImportClassPath;

public class FullPathCallingReportJson {
    public String importPath;
    public ImportArtifactJson artifact;

    public FullPathCallingReportJson(FullPathCallingReport report) {
        this.importPath = report.importClassPath.getPath();
        this.artifact = new ImportArtifactJson(report.fromArtifact);
    }
}
