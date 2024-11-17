package org.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.analyzer.models.*;
import org.analyzer.models.json.ProjectReportJson;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.analyzer.FileUtils.getFileList;

public class ProjectImportChecker {
    private StaticImportInspectorFromJar staticImportInspector;
    private List<Dependency> dependencies;
    private List<ImportArtifact> artifacts = new ArrayList<>();
    private final ArtifactInstaller artifactInstaller = new ArtifactInstaller();
    private List<Path> projectFileList = new ArrayList<>();
    private List<DependencyResolverReport> reportList = new ArrayList<>();
    private String projectArtifact;
    private String repoPath;
    private String repoSubPath;
    private String artifactLocation;

    public ProjectImportChecker(String repoPath, String repoSubPath, String destinationPath, Boolean installProjectArtifact, String projectArtifactId, Optional<String> jarPath) throws Exception {
        this.projectArtifact = projectArtifactId;
        this.repoPath = repoPath;
        this.repoSubPath = repoSubPath;
        this.artifactLocation = destinationPath;
        this.dependencies = GradleDependenciesExtractor.getProjectDependencies(repoPath);
        if (jarPath.isPresent()) {
            this.staticImportInspector = new StaticImportInspectorFromJar(new ArrayList<>(Arrays.asList(new File(jarPath.get()))));
        } else {
            this.staticImportInspector = new StaticImportInspectorFromJar(new ArrayList<>());
        }
        var extractedProjectArtifactDependency = GradleDependenciesExtractor.extractDependency(projectArtifactId);
        if (installProjectArtifact) {
            try {
                var artifact = artifactInstaller.install(extractedProjectArtifactDependency, destinationPath, false).a;
                artifacts.add(artifact);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        ImportArtifact pomFile = null;
        try {
            pomFile = artifactInstaller.install(extractedProjectArtifactDependency, destinationPath, true).a;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        ImportArtifact finalPomFile = pomFile;
        dependencies.forEach(d -> {
            var extractedDependency = GradleDependenciesExtractor.extractDependency(d.toString());
            try {
                var version = PomReader.getVersionFromPom(finalPomFile.getArtifactDirectory(), extractedDependency.getGroupId(), extractedDependency.getArtifactId());
                if (version != null) {
                    extractedDependency.setVersion(version);
                }
                var possibleVersion = ArtifactInstaller.fetchMetadata(extractedDependency);
                var nearestVersion = ArtifactInstaller.findNearest(extractedDependency.getVersion(), possibleVersion);
                extractedDependency.setVersion(nearestVersion);
                var installResult = artifactInstaller.install(extractedDependency, destinationPath, false);
                var artifact = installResult.a;
                var installExitCode = installResult.b;
                if (!artifacts.contains(artifact)) {
                    artifacts.add(artifact);
                }
            } catch (Exception e) {
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
        List<DependencyResolverReport> fileImportReports = new ArrayList<>();
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
            this.reportList.add(new DependencyResolverReport(path, unusedImportDeclaration, usedImportDeclaration, resolver.checkFullPathCalling, importList, resolver.fileImports));
        }
    }

    public List<FileImportReport> mapImportToPackage() throws Exception {
        List<FileImportReport> fileImportReports = new ArrayList<>();
        if (reportList.isEmpty()) {
            throw new Exception("Please resolve dependency before calling check import");
        }
        for (DependencyResolverReport report : reportList) {
            List<UseImportReport> useImportReports = new ArrayList<>();
            report.useImportObject.stream().distinct().forEach(obj -> {
                var classPath = obj.classPath.getPath();
                var isFound = false;
                for (ImportArtifact artifact: this.artifacts) {
                    try {
                        if (isPathInArtifact(classPath, artifact)) {
                            useImportReports.add(new UseImportReport(obj, artifact));
                            isFound = true;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!isFound) {
                    useImportReports.add(new UseImportReport(obj, null));
                }
            });

            List<UnusedImportReport> unusedImportReports = new ArrayList<>();
            report.unusedImports.forEach(impDecl -> {
                var classPath = new ImportClassPath(impDecl.toString().trim()).getPath();
                var isFound = false;
                for (ImportArtifact artifact: this.artifacts) {
                    try {
                        if (isPathInArtifact(classPath, artifact)) {
                            unusedImportReports.add(new UnusedImportReport(impDecl, artifact));
                            isFound = true;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!isFound) {
                    unusedImportReports.add(new UnusedImportReport(impDecl, null));
                }
            });


            List<FullPathCallingReport> fullPathCallingReports = new ArrayList<>();
            report.fullPathCalling.stream().filter(p -> p.contains(".")).distinct().forEach(calling -> {
                for (ImportArtifact artifact: this.artifacts) {
                    try {
                        if (isFullPathCallingFromArtifact(calling, artifact)) {
                            fullPathCallingReports.add(new FullPathCallingReport(new ImportClassPath(calling), artifact));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            var fileImportReport = new FileImportReport(useImportReports.toArray(UseImportReport[]::new), unusedImportReports.toArray(UnusedImportReport[]::new), fullPathCallingReports.toArray(FullPathCallingReport[]::new), report.filePath);
            fileImportReports.add(fileImportReport);
        }
        return fileImportReports;
    }

    public ProjectReport check() throws Exception {
        var fileImportList = mapImportToPackage();
        var useImportReportList = fileImportList.stream().flatMap(f -> Arrays.stream(f.useImportReport)).toList();
        var useArtifact = useImportReportList.stream().map(t -> t.fromArtifact).distinct().filter(Objects::nonNull).toList();
        var fullParhCallingReportList = fileImportList.stream().flatMap(f -> Arrays.stream(f.fullPathCallingReport)).toList();
        var fullPathCallingArtifact = fullParhCallingReportList.stream().map(t -> t.fromArtifact).distinct().toList();
        List<ImportArtifact> combinedUseImportList = new ArrayList<>();
        combinedUseImportList.addAll(useArtifact);
        combinedUseImportList.addAll(fullPathCallingArtifact);
        combinedUseImportList = combinedUseImportList.stream().distinct().toList();

        List<ImportArtifact> unusedArtifact = new ArrayList<>();
        for (ImportArtifact artifact : this.artifacts) {
            if (!combinedUseImportList.contains(artifact)) {
                unusedArtifact.add(artifact);
            }
        }
        return new ProjectReport(projectArtifact, repoPath, repoSubPath, artifactLocation, fileImportList.toArray(FileImportReport[]::new), combinedUseImportList.toArray(ImportArtifact[]::new), unusedArtifact.toArray(ImportArtifact[]::new));
    }

    public Boolean isPathInArtifact(String path, ImportArtifact artifact) throws Exception {
        var artifactClassList = staticImportInspector.getAllClassPathFromJarFile(artifact.getArtifactDirectory());
        artifactClassList = artifactClassList.stream().map(c -> c.replace("$", ".").replace("/", ".")).toList();
        for (String artifactClass : artifactClassList) {
            if (artifactClass.contains(path)) {
                return true;
            }
        }
        return false;
    }

    public Boolean isFullPathCallingFromArtifact(String path, ImportArtifact artifact) throws Exception {
        var artifactClassList = staticImportInspector.getAllClassPathFromJarFile(artifact.getArtifactDirectory());
        artifactClassList = artifactClassList.stream().map(c -> c.replace("$", ".").replace("/", ".").replace(".class", "")).toList();
        for (String artifactClass : artifactClassList) {
            if (path.contains(artifactClass)) {
                return true;
            }
        }
        return false;
    }

    public void exportToJson(ProjectReport projectReport, String destinationDir) throws Exception {
        ProjectReportJson projectReportJson = new ProjectReportJson(projectReport);
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String json = ow.writeValueAsString(projectReportJson);

        String filePath = destinationDir + "/" + projectReportJson.projectArtifact + ".json";

        // Write the JSON string to the file
        Files.write(Paths.get(filePath), json.getBytes());

        System.out.println("JSON written to file: " + filePath);
    }

    public void checkProjectImports() throws Exception {
        if (reportList.isEmpty()) {
            throw new Exception("Please resolve dependency before calling check import");
        }

        List<ImportArtifact> usingArtifact = new ArrayList<>();
        List<SingleImportDetails> usingImportDetails = reportList.stream().flatMap(r -> r.useImportObject.stream()).toList();
        List<String> usingImport = usingImportDetails.stream().map(r -> r.classPath.getPath()).distinct().toList();
        // Loop check each artifact
        for (ImportArtifact artifact : this.artifacts) {
            var artifactClassList = staticImportInspector.getAllClassPathFromJarFile(artifact.getArtifactDirectory());

            // Check classes in artifact with imports
            artifactClassList = artifactClassList.stream().map(c -> c.replace("$", "/")).toList();
            for (String artifactClass : artifactClassList) {
                for (String importPath : usingImport) {
                    if (artifactClass.contains(importPath.replace(".", "/"))) {
                       usingArtifact.add(artifact);
                    }
                }
            }
        }

        List<ImportArtifact> unusedArtifact = new ArrayList<>();
        for (ImportArtifact artifact : this.artifacts) {
            if (!usingArtifact.contains(artifact)) {
                unusedArtifact.add(artifact);
            }
        }

        unusedArtifact.forEach(u -> System.out.println("Unused artifact: " + u.getGroupId() + ":" + u.getArtifactId() + ":" + u.getVersion()));
    }

    public List<DependencyResolverReport> getReportList() {
        return reportList;
    }

    public StaticImportInspectorFromJar getStaticImportInspector() {
        return staticImportInspector;
    }

    public static void main(String[] args) throws Exception {
        var projectArtifact = "us.ihmc:ihmc-perception:0.14.0-241016";
        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception";
        var subPath = "/src";
        var jarPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar";
        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/temp-repo";
        var checker = new ProjectImportChecker(repoPath, subPath, destinationPath, false, projectArtifact, Optional.of(jarPath));
        checker.resolve(false);
        var projectReport = checker.check();
        var writeFileDestination = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/dependency-output";
        checker.exportToJson(projectReport, writeFileDestination);
//        var fileReports = checker.mapImportToPackage();
//
//        var firstFileReports = fileReports.getFirst();
//        System.out.println(firstFileReports.filePath.toAbsolutePath());
//        System.out.println("Use import report:");
//        Arrays.stream(firstFileReports.useImportReport).forEach(System.out::println);
//
//        System.out.println("Unused import report:");
//        Arrays.stream(firstFileReports.unusedImportReport).forEach(System.out::println);
    }
}
