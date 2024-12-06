package org.analyzer;

import org.analyzer.models.Dependency;
import org.analyzer.models.ImportArtifact;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenDependenciesExtractor {

    public static List<Dependency> getProjectDependencies(String repoPath, String projectArtifactId) throws Exception {
        // Run the 'gradle dependencies' command at the specified repository
        System.out.println("getting maven dependencies");
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("mvn", "dependency:list", "-DexcludeTransitive=true");
        processBuilder.directory(new File(repoPath));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Capture the output of the command
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            System.out.println(line);
            output.append(line).append("\n");
        }

        // Wait for the process to finish
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("Maven command failed with exit code: " + exitCode);
            throw new Exception("Maven command failed with exit code: " + exitCode);
        }

        // Extract dependencies using regex
        String dependenciesOutput = output.toString();
        return extractApiDependenciesBlock(dependenciesOutput, projectArtifactId);
    }

    public static void getAllProjectDependencies(ImportArtifact artifact) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("mvn", "dependency:get", getCommand(artifact));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            System.out.println(line);
            output.append(line).append("\n");
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("Maven command failed with exit code: " + exitCode);
            throw new Exception("Maven command failed with exit code: " + exitCode);
        }

        String dependenciesOutput = output.toString();
    }

    private static String getCommand(ImportArtifact artifact) {
        return "-Dartifact=" + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }

    private static List<Dependency> extractApiDependenciesBlock(String input, String projectArtifactId) {
        String startPattern = "@ " + projectArtifactId;
        String regex5 = "^([\\w\\.-]+:[\\w\\.-]+:[\\w\\.-]+:[\\w\\.-]+:[a-z]+)";
        String regex6 = "^([\\w\\.-]+:[\\w\\.-]+:[\\w\\.-]+:[\\w\\.-]+:[\\w\\.-]+:[a-z]+)";
        Pattern pattern5 = Pattern.compile(regex5);
        Pattern pattern6 = Pattern.compile(regex6);
        List<Dependency> dependencies = new ArrayList<>();
        boolean capturing = false;

        for (String line : input.split("\n")) {
            line = line.replace("[INFO]", "").trim();
            if (line.contains(startPattern)) {
                capturing = true;
                continue;
            }
            if (capturing) {
                if (line.contains("---------------------------------------------------------")) {
                    break;
                }
                Matcher matcher5 = pattern5.matcher(line.trim());
                if (matcher5.find()) {
                    var extractedLine = matcher5.group(1).split(":");
                    // Extract the parts using capturing groups
                    String groupId = extractedLine[0]; // org.bytedeco
                    String artifactId = extractedLine[1]; // opencv
                    String version = extractedLine[3]; // 4.7.0-1.5.9
                    String scope = extractedLine[4]; // 4.7.0-1.5.9

                    Dependency dependency = new Dependency(groupId + ":" + artifactId, version);
                    if (!Objects.equals(scope, "test") && !Objects.equals(scope, "runtime")) {
                        dependencies.add(dependency);
                    }
                }
                Matcher matcher6 = pattern6.matcher(line.trim());
                if (matcher6.find()) {
                    var extractedLine = matcher6.group(1).split(":");
                    // Extract the parts using capturing groups
                    String groupId = extractedLine[0]; // org.bytedeco
                    String artifactId = extractedLine[1]; // opencv
                    String version = extractedLine[4]; // 4.7.0-1.5.9
                    String scope = extractedLine[5]; // 4.7.0-1.5.9

                    Dependency dependency = new Dependency(groupId + ":" + artifactId, version);
                    if (!Objects.equals(scope, "test") && !Objects.equals(scope, "runtime")) {
                        dependencies.add(dependency);
                    }
                }
            }
        }
        return dependencies;
    }
}
