package org.analyzer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.analyzer.FileUtils.writeInputToFile;
import static org.analyzer.GitUtils.*;
import static org.analyzer.PomUtils.*;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void runSingleProject(String destinationPath, String writeFileDestination, String projectArtifact, String repoBasePath, String repoPath, String subPath, Boolean isPresentOnMavenRepo) throws Exception {
        String uri = "neo4j://localhost:7687";
        String username = "neo4j";
        String password = "12345678";
        Neo4jConnector neo4jConnector = new Neo4jConnector(uri, username, password);
        var checker = new ProjectImportChecker(neo4jConnector, repoBasePath, repoPath, subPath, destinationPath, true, projectArtifact, new HashMap<>() {}, isPresentOnMavenRepo);
        checker.resolve(false);
        var projectReport = checker.mapImport();
        checker.exportToJson(projectReport, writeFileDestination);
        neo4jConnector.close();
    }

    public static void runSingleProjectWithCSV(Integer csvIndex, String filePath, String destinationPath, String jsonFile, String writeFileDestination, String repoTarget) throws Exception {
        System.out.println("Running project with index: " + csvIndex);
        var inputMap = getProjectInputs(filePath, csvIndex);
        if (inputMap.isEmpty()) {
            System.out.println("Skipping index: " + csvIndex);
            return;
        }
        var artifactId = inputMap.get("artifactId");
        System.out.println("Running artifact: " + inputMap);
        var artifactName = artifactId.split(":")[1];
        var projectOwner = inputMap.get("projectOwner");
        var projectName = inputMap.get("projectName");
        var comparedVersion = inputMap.get("comparedVersion");
        var olderTags = inputMap.get("olderTags");
        comparedVersion = comparedVersion.replace("'", "\"").replace("None", "null");
        if (comparedVersion.isEmpty() || comparedVersion.contains("null") || comparedVersion.contains("None")) {
            System.out.println("Skipping project: " + artifactId);
            return;
        }
        // Parse the string into a nested list
        ObjectMapper mapper = new ObjectMapper();
        List<List<String>> parsedList = mapper.readValue(comparedVersion, new TypeReference<List<List<String>>>() {});
        List<String> parsedOlderTags = mapper.readValue(olderTags.replace("'", "\""), new TypeReference<List<String>>() {});

        if (parsedList.isEmpty()) {
            System.out.println("Skipping project: " + artifactId);
            return;
        }
        try {
            var repoPath = repoTarget + "/" + artifactId;
            cloneGitRepository(projectOwner, projectName, repoPath);
            var completedVersion = new ArrayList<>();
            for (List<String> version: parsedList) {
                var gitTag = version.get(1);
                var artifactVersion = version.get(0);
                if(gitTag == null || artifactVersion == null) {
                    System.out.println("Skipping " + artifactId);
                    return;
                }
                var checkFilePath = writeFileDestination + "/" + artifactId + "/" + artifactVersion + ".json";
                gitCheckoutBranch(repoPath, gitTag);
                var matchedPoms = findPomsWithArtifactId(repoPath, artifactName).getFirst();
                var repoArtifactPath = matchedPoms.replace("/pom.xml", "");
                completedVersion.add(artifactVersion);
                if (new File(checkFilePath).exists()) {
                    continue;
                }
                if (findPomFiles(repoArtifactPath).size() != 1) {
                    System.out.println("Skipping " + artifactId);
                    return;
                }
                System.out.println("Running at path: " + repoArtifactPath);
                runSingleProject(destinationPath, writeFileDestination, artifactId + ":" + artifactVersion, repoPath, repoArtifactPath, "", false);
                runGitRestore(repoPath);
            }

            System.out.println("Running older tags");
            for (String tag: parsedOlderTags) {
                var checkFilePath = writeFileDestination + "/" + artifactId + "/" + tag + ".json";
                gitCheckoutBranch(repoPath, tag);
                var pomResult = findPomsWithArtifactId(repoPath, artifactName);
                if (pomResult.size() != 1) {
                    continue;
                }
                var matchedPoms = pomResult.getFirst();
                var repoArtifactPath = matchedPoms.replace("/pom.xml", "");
                if (extractedVersion(repoArtifactPath, completedVersion, artifactId)) continue;
                if (findPomFiles(repoArtifactPath).size() != 1) {
                    System.out.println("Skipping " + artifactId);
                    return;
                }
                if (new File(checkFilePath).exists()) {
                    continue;
                }
                System.out.println("Running at path: " + repoArtifactPath);
                runSingleProject(destinationPath, writeFileDestination, artifactId + ":" + tag, repoPath, repoArtifactPath, "", false);
                runGitRestore(repoPath);
            }
            addDataToCsv(artifactId, repoPath, writeFileDestination + "/success_project.csv");
        } catch (Exception | Error e) {
            System.out.println(e.getMessage());
            addDataToCsv(artifactId, "", writeFileDestination + "/fail_project.csv");
            System.out.println("Uncompleted artifact: " + artifactId);
        }

    }

    private static boolean extractedVersion(String repoArtifactPath, ArrayList<Object> completedVersion, String artifactId) {
        try {
            var version = RepositoryUtils.getProjectVersions(repoArtifactPath);
            if (!version.isEmpty() && !version.contains("null") && !(version.contains("{") && version.contains("}"))) {
                if (completedVersion.contains(version)) {
                    System.out.println("Skipping because this version already completed " + artifactId);
                    return true;
                } else {
                    completedVersion.add(version);
                    System.out.println("Completed version: " + completedVersion);
                }
            }

        } catch (Exception | Error e) {
            log.warn(String.valueOf(e));
            return false;
        }
        return false;
    }

    private static Map<String, String> getProjectInputs(String filePath, int rowIndex) {
        try {
            // Read and parse the CSV file
            String[] headersToInclude = {"artifactId", "unused8", "unused9", "unused10", "unused11", "unused12", "unused13", "unused14", "projectOwner", "projectName", "unused15", "unused16", "unused17", "unused18", "unused19", "comparedVersion", "olderTags"};
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

    public static void addDataToCsv(String projectArtifact, String outputDir, String writeFileDir) {
        String[] newRow = {projectArtifact, outputDir}; // New row to append

        try (CSVWriter writer = new CSVWriter(new FileWriter(writeFileDir, true))) {
            writer.writeNext(newRow);
            System.out.println("Row appended successfully using OpenCSV.");
        } catch (IOException e) {
            System.err.println("Error appending to CSV: " + e.getMessage());
        }
    }

    public static List<String> getFileList(String directoryPath) {
        List<String> fileList = new ArrayList<>();

        // Create a File object for the directory
        File directory = new File(directoryPath);

        // Check if the path is a valid directory
        if (directory.exists() && directory.isDirectory()) {
            // Get the list of files and directories
            File[] files = directory.listFiles();

            if (files != null) {
                for (File file : files) {
                    // Add only files to the list (exclude directories)
                    if (file.isFile()) {
                        var path = file.getAbsolutePath().split("/");
                        fileList.add(path[path.length - 1].split(".json")[0]);
                    }
                }
            }
        } else {
            System.out.println("Invalid directory path: " + directoryPath);
        }

        return fileList;
    }


    public static void main(String[] args) throws Exception {

    }
}
