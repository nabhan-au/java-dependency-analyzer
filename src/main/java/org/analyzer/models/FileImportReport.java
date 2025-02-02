package org.analyzer.models;

import java.nio.file.Path;

public class FileImportReport {
    public UseImportReport[] useImportReport;
    public UnusedImportReport[] unusedImportReport;
    public FullPathCallingReport[] fullPathCallingReport;
    public Path filePath;
    public FailImportReport[] failImportReport;

    public FileImportReport(UseImportReport[] useImportReport, UnusedImportReport[] unusedImportReport, FullPathCallingReport[] fullPathCallingReport, Path filePath, FailImportReport[] failImportReport) {
        this.useImportReport = useImportReport;
        this.unusedImportReport = unusedImportReport;
        this.fullPathCallingReport = fullPathCallingReport;
        this.filePath = filePath;
        this.failImportReport = failImportReport;
    }
}

