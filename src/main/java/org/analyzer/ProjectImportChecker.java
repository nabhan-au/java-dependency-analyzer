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

    public ProjectImportChecker(String repoPath, String repoSubPath, String destinationPath, Boolean skipArtifactInstallation, String projectArtifactId, Optional<String> jarPath) throws Exception {
        var basePath = destinationPath + "/" + projectArtifactId;
        this.projectArtifact = projectArtifactId;
        this.repoPath = repoPath;
        this.repoSubPath = repoSubPath;
        this.artifactLocation = basePath;
        this.dependencies = DependencyExtractor.getProjectDependencies(repoPath);
        var artifactFiles = new ArrayList<File>();
        jarPath.ifPresent(s -> artifactFiles.add(new File(s)));
        var extractedProjectArtifactDependency = GradleDependenciesExtractor.extractDependency(projectArtifactId);
        System.out.println(dependencies);
        System.out.println("Downloading project artifact");
        new File(basePath).mkdirs();
        ImportArtifact finalPomFile;
        if (!skipArtifactInstallation) {
//            try {
//                var artifact = artifactInstaller.install(new ImportArtifact(extractedProjectArtifactDependency), basePath, false).a;
//                artifactFiles.add(new File(artifact.getArtifactPath()));
//            } catch (IOException | InterruptedException e) {
//                throw new RuntimeException(e);
//            }

            ImportArtifact pomFile = null;
            try {
                pomFile = artifactInstaller.install(new ImportArtifact(extractedProjectArtifactDependency), basePath, true, false).a;
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            finalPomFile = pomFile;
            PomUtils.getModifiedPomFile(finalPomFile.getArtifactPath());
            System.out.println("Downloading project dependencies");
            artifactInstaller.copyProjectArtifact(basePath, extractedProjectArtifactDependency);
            artifactInstaller.copyDependencies(basePath, finalPomFile);
        } else {
            finalPomFile = PomUtils.getPomFromPath(extractedProjectArtifactDependency.getGroupId(), extractedProjectArtifactDependency.getArtifactId(), extractedProjectArtifactDependency.getVersion(), basePath);
        }
        dependencies.forEach(d -> {
            var extractedDependency = GradleDependenciesExtractor.extractDependency(d.toString());
            try {
                var result = artifactInstaller.getArtifactFromPath(extractedDependency.getGroupId(), extractedDependency.getArtifactId(), extractedDependency.getVersion(), basePath);
                if (result == null) {
                    var version = PomReader.getVersionFromPom(finalPomFile.getArtifactPath(), extractedDependency.getGroupId(), extractedDependency.getArtifactId());
                    if (version != null) {
                        extractedDependency.setVersion(version);
                    }
                    var possibleVersion = ArtifactInstaller.fetchMetadata(extractedDependency);
                    var nearestVersion = ArtifactInstaller.findNearest(extractedDependency.getVersion(), possibleVersion);
                    extractedDependency.setVersion(nearestVersion);
                    var installResult = artifactInstaller.install(extractedDependency, basePath, false, true);
                    var artifact = installResult.a;
                    if (!artifacts.contains(artifact)) {
                        artifacts.add(artifact);
                        artifactFiles.add(new File(artifact.getArtifactPath()));
                    }
                } else {
                    artifacts.addAll(result);
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        var artifactPath = FileUtils.getJarPathList(basePath);
        artifactFiles.addAll(artifactPath.stream().map(a -> new File(a.toAbsolutePath().toString())).toList());
        this.projectFileList = getFileList(repoPath + repoSubPath);
        this.staticImportInspector = new StaticImportInspectorFromJar(artifactFiles);
    }

    public void resolve(Boolean debug) throws FileNotFoundException {
        this.reportList = new ArrayList<>();
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        ParserConfiguration parserConfig = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        StaticJavaParser.setConfiguration(parserConfig);
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
            var failImport = resolver.failImports.stream().map(f -> f.getOriginalPath().trim()).distinct().toList();

            var unusedImportDeclaration = new ArrayList<ImportDeclaration>();
            var usedImportDeclaration = new ArrayList<ImportDeclaration>();
            for (ImportDeclaration importDeclaration : fileImport) {
                if (!checkedImportList.contains(importDeclaration.toString().trim()) && !failImport.contains(importDeclaration.toString().trim())) {
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
            this.reportList.add(new DependencyResolverReport(path, unusedImportDeclaration, usedImportDeclaration, resolver.checkFullPathCalling, importList, resolver.fileImports, resolver.failImports));
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

            var failImportReport = report.failedImports.stream().map(FailImportReport::new).toArray(FailImportReport[]::new);
            var fileImportReport = new FileImportReport(useImportReports.toArray(UseImportReport[]::new), unusedImportReports.toArray(UnusedImportReport[]::new), fullPathCallingReports.toArray(FullPathCallingReport[]::new), report.filePath, failImportReport);
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
        combinedUseImportList = combinedUseImportList.stream().distinct().toList();
        unusedArtifact = unusedArtifact.stream().distinct().toList();
        return new ProjectReport(projectArtifact, repoPath, repoSubPath, artifactLocation, fileImportList.toArray(FileImportReport[]::new), combinedUseImportList.toArray(ImportArtifact[]::new), unusedArtifact.toArray(ImportArtifact[]::new));
    }

    public Boolean isPathInArtifact(String path, ImportArtifact artifact) throws Exception {
        var artifactClassList = staticImportInspector.getAllClassPathFromJarFile(artifact.getArtifactPath());
        artifactClassList = artifactClassList.stream().map(c -> c.replace("$", ".").replace("/", ".")).toList();
        for (String artifactClass : artifactClassList) {
            if (artifactClass.contains(path)) {
                return true;
            }
        }
        return false;
    }

    public Boolean isFullPathCallingFromArtifact(String path, ImportArtifact artifact) throws Exception {
        var artifactClassList = staticImportInspector.getAllClassPathFromJarFile(artifact.getArtifactPath());
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
            var artifactClassList = staticImportInspector.getAllClassPathFromJarFile(artifact.getArtifactPath());

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
        var projectArtifact = "us.ihmc:ihmc-perception:0.14.0-240126";
        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception";
        var subPath = "/src/main/java";
        var jarPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar";
        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/jar_repository";
        var checker = new ProjectImportChecker(repoPath, subPath, destinationPath, true, projectArtifact, Optional.empty());
        checker.resolve(false);
        var projectReport = checker.check();
        var writeFileDestination = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/dependency-output";
        checker.exportToJson(projectReport, writeFileDestination);


//        var jarPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/junit4/out/artifacts/junit_jar/junit.jar";
//        var projectArtifact = "junit:junit:4.13.2";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/junit4";
//        var subPath = "/src/main/java";
//        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/temp-repo";
//        var checker = new ProjectImportChecker(repoPath, subPath, destinationPath, false, projectArtifact, Optional.of(jarPath));
//        checker.resolve(true);
//        var projectReport = checker.check();
//        var writeFileDestination = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/dependency-output/test";
//        checker.exportToJson(projectReport, writeFileDestination);

//        var jarPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/junit4/out/artifacts/junit_jar/junit.jar";
//        var projectArtifact = "org.hamcrest:hamcrest-all:1.3";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/junit4";
//        var subPath = "/src/main/java";
//        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/temp-repo";
//        var checker = new ProjectImportChecker(repoPath, subPath, destinationPath, false, projectArtifact, Optional.of(jarPath));
//        checker.resolve(true);
//        var projectReport = checker.check();
//        var writeFileDestination = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/dependency-output/test";
//        checker.exportToJson(projectReport, writeFileDestination);


//        /Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/SecurityApi/out/artifacts/security_api_spring_boot_starter_jar/security-api-spring-boot-starter.jar

//        var jarPath = "/Users/nabhansuwanachote/.m2/repository/com/azure/azure-core/1.36.0/azure-core-1.36.0.jar";
//        var projectArtifact = "com.dimajix.flowman.maven:flowman-provider-azure:0.4.0";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/flowman-maven/flowman-provider-azure";
//        var subPath = "/src/main";
//        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/temp-repo";
//        var checker = new ProjectImportChecker(repoPath, subPath, destinationPath, true, projectArtifact, Optional.empty());
//        checker.resolve(true);
//        var projectReport = checker.check();
//        var writeFileDestination = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/dependency-output";
//        checker.exportToJson(projectReport, writeFileDestination);

//        var jarPath = "/Users/nabhansuwanachote/.m2/repository/org/webpieces/server/http-auth0login/2.1.109/http-auth0login-2.1.109.jar";
//        var projectArtifact = "org.webpieces.server:http-auth0login:2.1.109";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/webpieces/webserver/http-auth0login";
//        var subPath = "/src/main";
//        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/temp-repo";
//        var checker = new ProjectImportChecker(repoPath, subPath, destinationPath, true, projectArtifact, Optional.of(jarPath));
//        checker.resolve(true);
//        var projectReport = checker.check();
//        var writeFileDestination = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/dependency-output";
//        checker.exportToJson(projectReport, writeFileDestination);

    }
}
