package org.analyzer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.analyzer.models.Dependency;
import org.analyzer.models.ImportArtifact;
import org.analyzer.models.SingleImportDetails;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.analyzer.Main.getFileList;

public class ProjectImportChecker {
    private StaticImportInspectorFromJar staticImportInspector;
    private List<Dependency> dependencies;
    private List<ImportArtifact> artifacts = new ArrayList<>();
    private final ArtifactInstaller artifactInstaller = new ArtifactInstaller();
    private List<Path> projectFileList = new ArrayList<>();
    private List<FileImportReport> reportList = new ArrayList<>();

    public ProjectImportChecker(String repoPath, String repoSubPath, String destinationPath, Optional<String> jarPath, Optional<String> projectArtifactId) throws Exception {
        this.dependencies = GradleDependenciesExtractor.getProjectDependencies(repoPath);
        if (jarPath.isPresent()) {
            this.staticImportInspector = new StaticImportInspectorFromJar(new ArrayList<>(Arrays.asList(new File(jarPath.get()))));
        } else {
            this.staticImportInspector = new StaticImportInspectorFromJar(new ArrayList<>());
        }
        if (projectArtifactId.isPresent()) {
           var extractedDependency = GradleDependenciesExtractor.extractDependency(projectArtifactId.get());
            try {
                var artifact = artifactInstaller.install(extractedDependency, destinationPath);
                artifacts.add(artifact);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        dependencies.forEach(d -> {
            var extractedDependency = GradleDependenciesExtractor.extractDependency(d.toString());
            try {
                var artifact = artifactInstaller.install(extractedDependency, destinationPath);
                artifacts.add(artifact);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        this.projectFileList = getFileList(repoPath + repoSubPath);
    }

    public void resolve(Boolean debug) throws FileNotFoundException {
        this.reportList = new ArrayList<>();
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        ParserConfiguration parserConfig = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        StaticJavaParser.setConfiguration(parserConfig);
        List<FileImportReport> fileImportReports = new ArrayList<>();
        for (Path path : this.projectFileList) {
            CompilationUnit cu = StaticJavaParser.parse(new File(path.toAbsolutePath().toString()));
            var resolver = new DependencyResolver(cu, this.staticImportInspector);
            var importList = new ArrayList<SingleImportDetails>();
            var unable = new ArrayList<String>();
            cu.walk(node -> {
                var result = resolver.resolveNodeType(node);
                importList.addAll(result.a);
                unable.addAll(result.b);
            });
            var checkedImportList = importList.stream().map(t -> t.classPath.getOriginalPath()).distinct().toList();
            var fileImport = resolver.fileImports;

            var unusedImportDeclaration = new ArrayList<ImportDeclaration>();
            var usedImportDeclaration = new ArrayList<ImportDeclaration>();
            for (ImportDeclaration importDeclaration : fileImport) {
                if (!checkedImportList.contains(importDeclaration.toString().trim())) {
                    unusedImportDeclaration.add(importDeclaration);
                }
            }
            for (ImportDeclaration importDeclaration : fileImport) {
                if (checkedImportList.contains(importDeclaration.toString().trim())) {
                    usedImportDeclaration.add(importDeclaration);
                }
            }

            if (!unusedImportDeclaration.isEmpty() && debug) {
                System.out.println(path.toUri());
                for (ImportDeclaration importDeclaration : unusedImportDeclaration) {
                    System.out.println("Unused import: " + importDeclaration);
                }
                System.out.println("--------------------------");
            }
            this.reportList.add(new FileImportReport(path, unusedImportDeclaration, usedImportDeclaration, resolver.checkFullPathCalling, importList, resolver.fileImports));
        }
    }

    public void checkProjectImports() throws Exception {
        if (reportList.isEmpty()) {
            throw new Exception("Please resolve dependency before calling check import");
        }
        reportList.get(0).fullImportList.forEach(fullImport -> {
            System.out.println("Full import: " + fullImport.importObject.toString());
        });

        System.out.println();
    }

    public List<FileImportReport> getReportList() {
        return reportList;
    }

    public static void main(String[] args) throws Exception {
        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception";
        var subPath = "/src/main/java";
        var jarPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar";
        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/temp-repo";
        var checker = new ProjectImportChecker(repoPath, subPath, destinationPath, Optional.of(jarPath), Optional.empty());
        checker.resolve(false);
        checker.checkProjectImports();
    }
}
