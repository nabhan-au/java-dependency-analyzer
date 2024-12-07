package org.analyzer;

import org.analyzer.models.Dependency;
import org.analyzer.models.ImportArtifact;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


// Not used anymore
public class GradleDependenciesExtractor {
    public static List<Dependency> getProjectDependencies(String repoPath , ImportArtifact projectArtifact) throws Exception {
        // Run the 'gradle dependencies' command at the specified repository
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("gradle", "dependencies");
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
            System.err.println("Gradle command failed with exit code: " + exitCode);
            throw new Exception("Gradle command failed with exit code: " + exitCode);
        }

        // Extract dependencies using regex
        String dependenciesOutput = output.toString();
        return extractApiDependenciesBlock(dependenciesOutput, projectArtifact);
    }

    private static List<Dependency> extractApiDependenciesBlock(String input, ImportArtifact projectArtifact) {
        String startPattern = "api - API dependencies for";
        String regex = "[+|\\\\]--- (.*?):(.*?):(.*?) \\(n\\)";
        String projectRegex = "\\+--- project (.*?) \\(n\\)";
        Pattern pattern = Pattern.compile(regex);
        Pattern projectPattern = Pattern.compile(projectRegex);
        List<Dependency> dependencies = new ArrayList<>();
        boolean capturing = false;

        for (String line : input.split("\n")) {
            if (line.startsWith(startPattern)) {
                capturing = true;
                continue;
            }
            if (capturing) {
                if (line.trim().isEmpty()) {
                    break;
                }
                Matcher matcher = pattern.matcher(line.trim());
                if (matcher.find()) {
                    // Extract the parts using capturing groups
                    String part1 = matcher.group(1); // org.bytedeco
                    String part2 = matcher.group(2); // opencv
                    String version = matcher.group(3); // 4.7.0-1.5.9

                    Dependency dependency = new Dependency(part1 + ":" + part2, version);
                    dependencies.add(dependency);
                } else {
                    // Handle project dependencies
                    Matcher projectMatcher = projectPattern.matcher(line.trim());
                    if (projectMatcher.find()) {
                        String projectName = projectMatcher.group(1); // Extract the project name (e.g., inject-resources-core)
                        String artifactId = projectArtifact.getGroupId() + ":" + projectName; // Construct groupId dynamically

                        Dependency dependency = new Dependency(artifactId, projectArtifact.getVersion());
                        dependencies.add(dependency);
                    }
                }
            }
        }
        return dependencies;
    }
}

