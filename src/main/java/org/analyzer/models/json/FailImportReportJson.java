package org.analyzer.models.json;

import org.analyzer.models.FailImportReport;

public class FailImportReportJson {
    public String importPath;

    public FailImportReportJson(FailImportReport failImportReport) {
        this.importPath = failImportReport.importClassPath.getOriginalPath();
    }
}
