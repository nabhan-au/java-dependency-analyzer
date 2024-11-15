package org.analyzer;

import com.github.javaparser.ast.ImportDeclaration;
import org.analyzer.models.SingleImportDetails;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileImportReport {
    public Path filePath;
    public List<ImportDeclaration> unusedImports;
    public List<ImportDeclaration> usedImports;
    public List<String> checkFullPathCalling;
    public List<SingleImportDetails> fullImportList;
    public List<ImportDeclaration> fullFileImportList;

    public FileImportReport(Path filePath, List<ImportDeclaration> unusedImports, List<ImportDeclaration> usedImports, List<String> checkFullPathCalling, List<SingleImportDetails> fullImportList, List<ImportDeclaration> fullFileImportList) {
        this.filePath = filePath;
        this.unusedImports = unusedImports;
        this.usedImports = usedImports;
        this.checkFullPathCalling = checkFullPathCalling;
        this.fullImportList = fullImportList;
        this.fullFileImportList = fullFileImportList;
    }
}
