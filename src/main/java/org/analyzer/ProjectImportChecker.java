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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.analyzer.FileUtils.*;

public class ProjectImportChecker {
    private StaticImportInspectorFromJar staticImportInspector;
    private List<Dependency> dependencies;
    private List<Dependency> transitiveDependencies;
    private List<ImportArtifact> artifacts = new ArrayList<>();
    private List<ImportArtifact> transitiveArtifacts = new ArrayList<>();
    private final ArtifactInstaller artifactInstaller = new ArtifactInstaller();
    private List<Path> projectFileList = new ArrayList<>();
    private Map<ImportArtifact, List<Dependency>> transitiveDependencyMap = new HashMap<>();
    private List<DependencyResolverReport> reportList = new ArrayList<>();
    private String projectArtifact;
    private String repoPath;
    private String repoSubPath;
    private String artifactLocation;
    private RepositoryUtils repositoryUtils;

    public ProjectImportChecker(Neo4jConnector neo4jConnector, String repoBasePath, String repoPath, String repoSubPath, String destinationPath, Boolean skipPomFileModification, String projectArtifactId, Map<String, String> dependencyMap, Boolean isPresentOnMavenRepo) throws Exception {
        var basePath = destinationPath + "/" + projectArtifactId;
        this.projectArtifact = projectArtifactId;
        this.repoPath = repoPath;
        this.repoSubPath = repoSubPath;
        this.artifactLocation = basePath;
        this.repositoryUtils = new RepositoryUtils(neo4jConnector);
        var artifactFiles = new ArrayList<File>();
        var extractedProjectArtifact = DependencyExtractor.extractDependency(projectArtifactId);
        System.out.println("Downloading project artifact");
        new File(basePath).mkdirs();
        ImportArtifact finalPomFile;
        ImportArtifact pomFile;
        if (isPresentOnMavenRepo) {
            try {
                pomFile = artifactInstaller.install(new ImportArtifact(extractedProjectArtifact), basePath, true, false).a;
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            pomFile = new ImportArtifact(extractedProjectArtifact.getArtifactId(), extractedProjectArtifact.getGroupId(), extractedProjectArtifact.getVersion(), repoPath + "/pom.xml");
        }
        finalPomFile = pomFile;
        if (!skipPomFileModification) {
            PomUtils.getModifiedPomFile(finalPomFile.getArtifactPath());
        }
        System.out.println("Downloading project dependencies");
        artifactInstaller.copyDependenciesWithRemoval(basePath, repoBasePath, finalPomFile);
        if (isPresentOnMavenRepo) {
            this.dependencies = repositoryUtils.getDependencyFromReleaseId(extractedProjectArtifact.toString());
            artifactInstaller.copyProjectArtifact(basePath, extractedProjectArtifact);
        } else {
            this.dependencies = DependencyExtractor.getProjectDependencies(repoPath, extractedProjectArtifact);
        }
        System.out.println("--------------------- Getting Direct dependencies ---------------------");
        System.out.println(dependencies);
        this.dependencies.forEach(d -> {
            var extractedDependency = DependencyExtractor.extractDependency(d.toString());
            try {
                var result = artifactInstaller.getArtifactFromPath(extractedDependency.getGroupId(), extractedDependency.getArtifactId(), extractedDependency.getVersion(), basePath, dependencyMap);
                if (result == null) {
                    System.out.println("Downloading dependency: " + d);
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
                    System.out.println("Found dependency: " + d);
                    artifacts.addAll(result);
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("------------------- Getting Transitive dependencies -------------------");
        var projectBasedFileObject = new File(basePath + "/dependencies");
        this.transitiveDependencies = listDependencyDirectory(projectBasedFileObject, projectBasedFileObject);
        this.transitiveDependencies.forEach(d -> {
            if (!dependencies.contains(d)) {
                var extractedDependency = DependencyExtractor.extractDependency(d.toString());
                try {
                    var result = artifactInstaller.getArtifactFromPath(extractedDependency.getGroupId(), extractedDependency.getArtifactId(), extractedDependency.getVersion(), basePath, dependencyMap);
                    if (result == null) {
                        System.out.println("Downloading transitive dependency: " + d);
                        var version = PomReader.getVersionFromPom(finalPomFile.getArtifactPath(), extractedDependency.getGroupId(), extractedDependency.getArtifactId());
                        if (version != null) {
                            extractedDependency.setVersion(version);
                        }
                        var possibleVersion = ArtifactInstaller.fetchMetadata(extractedDependency);
                        var nearestVersion = ArtifactInstaller.findNearest(extractedDependency.getVersion(), possibleVersion);
                        extractedDependency.setVersion(nearestVersion);
                        var installResult = artifactInstaller.install(extractedDependency, basePath, false, true);
                        var artifact = installResult.a;
                        if (!transitiveArtifacts.contains(artifact)) {
                            transitiveArtifacts.add(artifact);
                            artifactFiles.add(new File(artifact.getArtifactPath()));
                        }
                    }
                    else {
                        System.out.println("Found transitive dependency: " + d.toString());
                        transitiveArtifacts.addAll(result);
                    }

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

        });
        System.out.println("--------------------- Map transitive dependencies ---------------------");
        this.dependencies.forEach(d -> {
            var transitive = repositoryUtils.getTransitiveDependency(d, 0);
            transitiveDependencyMap.put(new ImportArtifact(d), transitive);
        });
        System.out.println("------------------------- Direct dependencies -------------------------");
        System.out.println(this.artifacts);
        System.out.println("----------------------- Transitive dependencies -----------------------");
        System.out.println(this.transitiveDependencies);

        System.out.println("Getting artifact from path: " + basePath);
        var artifactPath = FileUtils.getJarPathList(basePath);
        artifactFiles.addAll(artifactPath.stream().map(a -> new File(a.toAbsolutePath().toString())).toList());
        this.projectFileList = getFileList(repoPath + repoSubPath);
        System.out.println("Project file size: " + projectFileList.size());
        System.out.println("Project file list: " + projectFileList.stream().map(f -> f.toUri().toString()).toList());
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
        var index = 0;
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
            index++;
            System.out.println("Current file index: " + index);
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
                mapUseImportClassPath(obj, classPath, useImportReports);
            });

            List<UnusedImportReport> unusedImportReports = new ArrayList<>();
            report.unusedImports.forEach(impDecl -> {
                var classPath = new ImportClassPath(impDecl.toString().trim()).getPath();
                mapUnusedImportClassPath(impDecl, classPath, unusedImportReports);
            });


            List<FullPathCallingReport> fullPathCallingReports = new ArrayList<>();
            report.fullPathCalling.stream().filter(p -> p.contains(".")).distinct().forEach(calling -> {
                for (ImportArtifact artifact: this.artifacts) {
                    try {
                        if (isFullPathCallingFromArtifact(calling, artifact)) {
                            fullPathCallingReports.add(new FullPathCallingReport(new ImportClassPath(calling), artifact, true));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                for (ImportArtifact artifact: this.transitiveArtifacts) {
                    try {
                        if (isFullPathCallingFromArtifact(calling, artifact)) {
                            fullPathCallingReports.add(new FullPathCallingReport(new ImportClassPath(calling), artifact, false));
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

    private void mapUnusedImportClassPath(ImportDeclaration impDecl, String classPath, List<UnusedImportReport> unusedImportReports) {
        var isFound = false;
        for (ImportArtifact artifact: this.artifacts) {
            try {
                if (isPathInArtifact(classPath, artifact)) {
                    unusedImportReports.add(new UnusedImportReport(impDecl, artifact, true));
                    isFound = true;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (ImportArtifact artifact: this.transitiveArtifacts) {
            try {
                if (isPathInArtifact(classPath, artifact)) {
                    unusedImportReports.add(new UnusedImportReport(impDecl, artifact, false));
                    isFound = true;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (!isFound) {
            unusedImportReports.add(new UnusedImportReport(impDecl, null, false));
        }
    }

    private void mapUseImportClassPath(SingleImportDetails obj, String classPath, List<UseImportReport> useImportReports) {
        var isFound = false;
        for (ImportArtifact artifact: this.artifacts) {
            try {
                if (isPathInArtifact(classPath, artifact)) {
                    useImportReports.add(new UseImportReport(obj, artifact, true));
                    isFound = true;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        for (ImportArtifact transitiveArtifact: this.transitiveArtifacts) {
            try {
                if (isPathInArtifact(classPath, transitiveArtifact)) {
                    useImportReports.add(new UseImportReport(obj, transitiveArtifact, false));
                    isFound = true;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (!isFound) {
            useImportReports.add(new UseImportReport(obj, null, false));
        }
    }

    public ProjectReport check() throws Exception {
        var fileImportList = mapImportToPackage();
        var useImportReportList = fileImportList.stream().flatMap(f -> Arrays.stream(f.useImportReport)).toList();
        var useArtifact = useImportReportList.stream().filter(t -> t.isDirectDependency).map(t -> t.fromArtifact).distinct().filter(Objects::nonNull).toList();
        var fullParhCallingReportList = fileImportList.stream().flatMap(f -> Arrays.stream(f.fullPathCallingReport)).toList();
        var fullPathCallingArtifact = fullParhCallingReportList.stream().map(t -> t.fromArtifact).distinct().toList();
        List<ImportArtifact> combinedUseImportList = new ArrayList<>();
        combinedUseImportList.addAll(useArtifact);
        combinedUseImportList.addAll(fullPathCallingArtifact);
        combinedUseImportList = combinedUseImportList.stream().distinct().toList();

        List<ImportArtifact> useTransitiveArtifact = new ArrayList<>();
        var undetectedDependency = useImportReportList.stream().filter(t -> !t.isDirectDependency).map(t -> t.fromArtifact).distinct().filter(Objects::nonNull).toList();
        for (ImportArtifact undetected: undetectedDependency) {
            for (ImportArtifact artifact: this.artifacts) {
                if (!combinedUseImportList.contains(artifact)) {
                    var transitiveDependencyList = transitiveDependencyMap.get(artifact).stream().map(ImportArtifact::new).distinct().toList();
                    if (transitiveDependencyList.contains(undetected)) {
                        useTransitiveArtifact.add(artifact);
                    }
                }
            }
        }

        List<ImportArtifact> unusedArtifact = new ArrayList<>();
        for (ImportArtifact artifact : this.artifacts) {
            if (!combinedUseImportList.contains(artifact) && !useTransitiveArtifact.contains(artifact)) {
                unusedArtifact.add(artifact);
            }
        }
        combinedUseImportList = combinedUseImportList.stream().distinct().toList();
        unusedArtifact = unusedArtifact.stream().distinct().toList();
        return new ProjectReport(projectArtifact, repoPath, repoSubPath, artifactLocation, fileImportList.toArray(FileImportReport[]::new), combinedUseImportList.toArray(ImportArtifact[]::new), useTransitiveArtifact.toArray(ImportArtifact[]::new), unusedArtifact.toArray(ImportArtifact[]::new));
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

        var splitArtifactId = projectReportJson.projectArtifact.split(":");
        var artifact = splitArtifactId[0] + ":" + splitArtifactId[1];
        var version = splitArtifactId[2];

        String filePath = destinationDir + "/" + artifact + "/" + version + ".json";

        System.out.println("Writing result to " + filePath);
        new File(destinationDir + "/" + artifact).mkdirs();
        // Write the JSON string to the file
        Files.write(Paths.get(filePath), json.getBytes());

        System.out.println("JSON written to file: " + filePath);
    }

    public static void main(String[] args) throws Exception {
        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/jar_repository";
        var writeFileDestination = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/new-dependency-output";
        var csvFile = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/datasets/artifact-dependency-details.csv";
        String uri = "neo4j://localhost:7687";
        String username = "neo4j";
        String password = "12345678";
        Neo4jConnector neo4jConnector = new Neo4jConnector(uri, username, password);
//        var projectArtifact = "us.ihmc:ihmc-perception:0.14.0-240126";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception";
//        var subPath = "/src/main/java";
//        var jarPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar";
//        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/jar_repository";
//        var checker = new ProjectImportChecker(repoPath, subPath, destinationPath, true, projectArtifact, Optional.empty());
//        checker.resolve(false);
//        var projectReport = checker.check();
//        var writeFileDestination = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/dependency-output";
//        checker.exportToJson(projectReport, writeFileDestination);

        var projectArtifact = "net.cactusthorn.config:jasypt:0.30";
        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/net.cactusthorn.config:jasypt/jasypt";
        var repoBasePath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/net.cactusthorn.config:jasypt";
        var subPath = "";
        var gitBranch = "v0.30";

        GitUtils.gitCheckoutBranch(repoPath, gitBranch);
        var checker = new ProjectImportChecker(neo4jConnector, repoBasePath, repoPath, subPath, destinationPath, true, projectArtifact, new HashMap<>(){}, false);
        checker.resolve(false);
        var projectReport = checker.check();
        checker.exportToJson(projectReport, writeFileDestination);

        // Path to the JSON file
        String jsonFilePath = "save_input.json";
        writeInputToFile(jsonFilePath, projectArtifact, repoPath, subPath, gitBranch);




//        var projectArtifact = "com.lithium.flow:flow:0.7.48";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/flow";
//        var subPath = "";

//        var projectArtifact = "org.appverse.web.framework.modules.backend.frontfacade.gwt:appverse-web-modules-backend-frontfacade-gwt:1.5.4-RELEASE";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/appverse-web/framework/modules/backend/front-facade/gwt";
//        var subPath = "";

//        var projectArtifact = "com.github.hammelion:jraml:0.3";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/jraml";
//        var subPath = "";

//        var projectArtifact = "com.github.ladutsko:isbn-core:1.5.2";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/isbn-core";
//        var subPath = "";

//        var projectArtifact = "com.insidecoding:sos:1.3.2";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/selenium-on-steroids";
//        var subPath = "";

//        var projectArtifact = "com.naturalprogrammer.cleanflow:cleanflow:1.5.4";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/cleanflow";
//        var subPath = "";

//        var projectArtifact = "com.phoenixnap.oss:springmvc-raml-parser:0.10.14";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/leshan/leshan-core-cf/springmvc-raml-plugin";
//        var subPath = "";

//        var projectArtifact = "org.eclipse.leshan:leshan-core-cf:2.0.0-M15";
//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/leshan/leshan-core-cf";
//        var subPath = "";











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
