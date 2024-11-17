package org.analyzer.models;

import java.nio.file.Path;

public class FileImportReport {
    public UseImportReport[] useImportReport;
    public UnusedImportReport[] unusedImportReport;
    public Path filePath;

    public FileImportReport(UseImportReport[] useImportReport, UnusedImportReport[] unusedImportReport, Path filePath) {
        this.useImportReport = useImportReport;
        this.unusedImportReport = unusedImportReport;
        this.filePath = filePath;
    }
}

