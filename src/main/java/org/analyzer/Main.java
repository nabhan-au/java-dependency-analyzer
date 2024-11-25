package org.analyzer;

import java.io.IOException;
import java.util.Optional;

import static org.analyzer.FileUtils.writeInputToFile;

public class Main {
    public static void runMultipleProjects(String jsonFilePath, String destinationPath, String writeFileDestination, String dependencyDetailsCsvFile) throws Exception {
        var projects = FileUtils.readProjectsFromJson(jsonFilePath);
        for (var project : projects) {
            runSingleProject(destinationPath, writeFileDestination, dependencyDetailsCsvFile, project.projectArtifact, project.repoPath, project.subPath, project.gitBranch, "save_input.json", false);
        }
    }

    public static void runSingleProject(String destinationPath, String writeFileDestination, String dependencyDetailsCsvFile, String projectArtifact, String repoPath, String subPath, String gitBranch, String jsonFileOutputPath, Boolean writeProjectToJson) throws Exception {
        GitUtils.gitCheckoutBranch(repoPath, gitBranch);
        var checker = new ProjectImportChecker(repoPath, subPath, destinationPath, false, true, projectArtifact, Optional.empty(), Optional.of(dependencyDetailsCsvFile));
        checker.resolve(false);
        var projectReport = checker.check();
        checker.exportToJson(projectReport, writeFileDestination);

        // Path to the JSON file
        if (writeProjectToJson) {
            writeInputToFile(jsonFileOutputPath, projectArtifact, repoPath, subPath, gitBranch);
        }
    }

    public static void main(String[] args) throws Exception {
        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/jar_repository";
        var writeFileDestination = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/new-dependency-output";
        var csvFile = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/datasets/artifact-dependency-details.csv";
        var jsonFile = "save_input.json";

        var projectArtifact = "org.zalando:jackson-module-unknown-property:0.2.1";
        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/jackson-module-unknown-property";
        var subPath = "/src/main";
        var gitBranch = "0.2.1";

        runSingleProject(destinationPath, writeFileDestination, csvFile, projectArtifact, repoPath, subPath, gitBranch, jsonFile, true);
//        runMultipleProjects(jsonFile, destinationPath, writeFileDestination, csvFile);
    }
}
