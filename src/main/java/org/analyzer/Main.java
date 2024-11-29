package org.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.analyzer.FileUtils.writeInputToFile;
import static org.analyzer.GitUtils.cloneGitRepository;
import static org.analyzer.GitUtils.gitCheckoutBranch;
import static org.analyzer.PomUtils.*;

public class Main {
    public static void runMultipleProjects(String jsonFilePath, String destinationPath, String writeFileDestination, String dependencyDetailsCsvFile) throws Exception {
        var projects = FileUtils.readProjectsFromJson(jsonFilePath);
        for (var project : projects) {
            runSingleProject(destinationPath, writeFileDestination, dependencyDetailsCsvFile, project.projectArtifact, project.repoPath, project.subPath, project.gitBranch, "save_input.json", false);
        }
    }

    public static void runSingleProject(String destinationPath, String writeFileDestination, String dependencyDetailsCsvFile, String projectArtifact, String repoPath, String subPath, String gitBranch, String jsonFileOutputPath, Boolean writeProjectToJson) throws Exception {
        GitUtils.gitCheckoutBranch(repoPath, gitBranch);
        String uri = "neo4j://localhost:7687";
        String username = "neo4j";
        String password = "12345678";
        Neo4jConnector neo4jConnector = new Neo4jConnector(uri, username, password);
        var checker = new ProjectImportChecker(neo4jConnector, repoPath, subPath, destinationPath, false, true, projectArtifact, Optional.empty(), Optional.of(dependencyDetailsCsvFile), new HashMap<>() {{
        }});
        checker.resolve(false);
        var projectReport = checker.check();
        checker.exportToJson(projectReport, writeFileDestination);

        // Path to the JSON file
        if (writeProjectToJson) {
            writeInputToFile(jsonFileOutputPath, projectArtifact, repoPath, subPath, gitBranch);
        }
    }

    public static void runSingleProjectWithCSV(Integer csvIndex, String filePath) throws Exception {
        System.out.println("Running project with index: " + csvIndex);
        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/jar_repository";
        var writeFileDestination = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/new-dependency-output";
        var csvFile = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/datasets/artifact-dependency-details.csv";
        var jsonFile = "save_input.json";
        var repoTarget = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo";
        var completePath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/new-dependency-output/complete.json";

        var inputMap = getProjectInputs(filePath, csvIndex);
        if (inputMap.isEmpty()) {
            System.out.println("Skipping index: " + csvIndex);
            return;
        }
        var artifactId = inputMap.get("artifactId");
        var artifactName = artifactId.split(":")[1];
        var projectOwner = inputMap.get("projectOwner");
        var projectName = inputMap.get("projectName");
        var comparedVersion = inputMap.get("comparedVersion");
        comparedVersion = comparedVersion.replace("'", "\"").replace("None", "null");
        if (comparedVersion.isEmpty() || comparedVersion.contains("null") || comparedVersion.contains("None")) {
            System.out.println("Skipping project: " + artifactId);
            return;
        }
        // Parse the string into a nested list
        ObjectMapper mapper = new ObjectMapper();
        List<List<String>> parsedList = mapper.readValue(comparedVersion, new TypeReference<List<List<String>>>() {});

        if (parsedList.isEmpty()) {
            System.out.println("Skipping project: " + artifactId);
            return;
        }
        try {
            var repoPath = repoTarget + "/" + artifactId;
            cloneGitRepository(projectOwner, projectName, repoPath);
            for (List<String> version: parsedList) {
                var gitTag = version.get(1);
                var artifactVersion = version.get(0);
                if(gitTag == null || artifactVersion == null) {
                    System.out.println("Skipping " + artifactId);
                    return;
                }
                gitCheckoutBranch(repoPath, gitTag);
                var matchedPoms = findPomsWithArtifactId(repoPath, artifactName).getFirst();
                var repoArtifactPath = matchedPoms.replace("/pom.xml", "");
                if (findPomFiles(repoArtifactPath).size() != 1) {
                    System.out.println("Skipping " + artifactId);
                    return;
                }
                System.out.println("Running at path: " + repoArtifactPath);
                runSingleProject(destinationPath, writeFileDestination, csvFile, artifactId + ":" + artifactVersion, repoArtifactPath, "", gitTag, jsonFile, false);
            }
            writeInputToFile(completePath, artifactId, repoPath, "", "");
        } catch (Exception | Error e) {
            System.out.println("Uncompleted artifact: " + artifactId);
        }

    }

    private static Map<String, String> getProjectInputs(String filePath, int rowIndex) {
        try {
            // Read and parse the CSV file
            String[] headersToInclude = {"unused1", "unused2", "unused3", "unused4", "unused5", "unused6", "artifactId", "unused8", "unused9", "unused10", "unused11", "unused12", "unused13", "unused14", "projectOwner", "projectName", "unused15", "unused16", "unused17", "unused18", "unused19", "comparedVersion"};
            CSVParser parser = CSVFormat.DEFAULT
                    .withHeader(headersToInclude) // Use the first row as header
                    .parse(new FileReader(filePath));

            int currentIndex = 0; // Track the current row index
            for (CSVRecord record : parser) {
                if (currentIndex == rowIndex) { // Check if this is the desired row
                    System.out.println("Row at index " + rowIndex + ":");
                    return record.toMap();
                }
                currentIndex++;
            }

        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
        } catch (IndexOutOfBoundsException e) {
            System.err.println("Invalid row index: " + e.getMessage());
        }
        return null;
    }


    public static void main(String[] args) throws Exception {
        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/jar_repository";
        var writeFileDestination = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/new-dependency-output";
        var csvFile = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/datasets/artifact-dependency-details.csv";
        var jsonFile = "save_input.json";

        var projectArtifact = "com.github.hxbkx:ExcelUtils:1.4.2";
        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ExcelUtils";
        var subPath = "/src";
        var gitBranch = "1.4.2";

        for (int i = 200; i < 201; i++) { // Equivalent to range(0, 10) in Python
            runSingleProjectWithCSV(i, "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/analyzer-python/data/test-compared-result.csv");
        }
//        runSingleProjectWithCSV(3, "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/analyzer-python/data/test-compared-result.csv");
//        runSingleProject(destinationPath, writeFileDestination, csvFile, projectArtifact, repoPath, subPath, gitBranch, jsonFile, false);
//        runMultipleProjects(jsonFile, destinationPath, writeFileDestination, csvFile);
    }
}
