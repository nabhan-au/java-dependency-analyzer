package org.analyzer.models.json;

import org.analyzer.models.FileImportReport;
import java.util.Arrays;

public class FileImportReportJson {
    public String  filePath;
    public UseImportReportJson[] useImportReport;
    public UnusedImportReportJson[] unusedImportReport;
    public FullPathCallingReportJson[] fullPathCallingReport;
    public FailImportReportJson[] failImportReport;

    public FileImportReportJson(FileImportReport fileImportReport) {
        this.filePath = fileImportReport.filePath.toAbsolutePath().toString();
        this.useImportReport = Arrays.stream(fileImportReport.useImportReport).map(UseImportReportJson::new).toArray(UseImportReportJson[]::new);
        this.unusedImportReport = Arrays.stream(fileImportReport.unusedImportReport).map(UnusedImportReportJson::new).toArray(UnusedImportReportJson[]::new);
        this.fullPathCallingReport = Arrays.stream(fileImportReport.fullPathCallingReport).map(FullPathCallingReportJson::new).toArray(FullPathCallingReportJson[]::new);
        this.failImportReport = Arrays.stream(fileImportReport.failImportReport).map(FailImportReportJson::new).toArray(FailImportReportJson[]::new);
    }
}
