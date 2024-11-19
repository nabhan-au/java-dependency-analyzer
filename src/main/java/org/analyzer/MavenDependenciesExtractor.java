package org.analyzer;

import org.analyzer.models.Dependency;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenDependenciesExtractor {

    public static List<Dependency> getProjectDependencies(String repoPath) throws Exception {
        // Run the 'gradle dependencies' command at the specified repository
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("mvn", "dependency:list");
        processBuilder.directory(new File(repoPath));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Capture the output of the command
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
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
        return extractApiDependenciesBlock(dependenciesOutput);
    }

    private static List<Dependency> extractApiDependenciesBlock(String input) {
        String startPattern = "The following files have been resolved:";
        String regex = "([\\w.]+):([\\w.-]+):([\\w.-]+):([\\w.-]+):([\\w.-]+) -- module ([\\w.-]+)";
        Pattern pattern = Pattern.compile(regex);
        List<Dependency> dependencies = new ArrayList<>();
        boolean capturing = false;

        for (String line : input.split("\n")) {
            line = line.replace("[INFO]", "").trim();
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
                    String groupId = matcher.group(1); // org.bytedeco
                    String artifactId = matcher.group(2); // opencv
                    String type = matcher.group(3); // 4.7.0-1.5.9
                    String version = matcher.group(4); // 4.7.0-1.5.9
                    String scope = matcher.group(5); // 4.7.0-1.5.9

                    Dependency dependency = new Dependency(groupId + ":" + artifactId, version);
                    if (!Objects.equals(scope, "test")) {
                        dependencies.add(dependency);
                    }
                }
            }
        }
        return dependencies;
    }
}
