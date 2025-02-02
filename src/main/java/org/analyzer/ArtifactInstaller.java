package org.analyzer;

import com.github.javaparser.utils.Pair;
import org.analyzer.models.ImportArtifact;

import java.util.Comparator;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.analyzer.FileUtils.getJarPathList;
import static org.analyzer.FileUtils.getPomPathFromDependencyDir;

public class ArtifactInstaller {
    public String getInstallUrl(String groupId, String artifactId, String version) {
        groupId = groupId.replace('.', '/');
        return "https://repo1.maven.org/maven2/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
    }

    public String getInstallPomUrl(String groupId, String artifactId, String version) {
        groupId = groupId.replace('.', '/');
        return "https://repo1.maven.org/maven2/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".pom";
    }

    public List<ImportArtifact> getArtifactFromPath(String groupId, String artifactId, String version, String basePath, Map<String, String> dependencyMap) {
        var replacedGroupId = groupId.replace('.', '/');
        var artifactDir = basePath + "/dependencies/" + replacedGroupId + "/" + artifactId;
        System.out.println(artifactDir);
        if (dependencyMap.containsKey(groupId + ":" + artifactId)) {
            artifactDir = basePath + "/dependencies/" + dependencyMap.get(groupId + ":" + artifactId).replace(':', '/').replace('.', '/');
        }
        var artifactPath = getJarPathList(artifactDir);
        if (artifactPath.isEmpty()) {
            try {
                getPomPathFromDependencyDir(artifactDir);
                return new ArrayList<>();
            } catch (Exception e) {
                return null;
            }
        }


        return artifactPath.stream().map(p -> new ImportArtifact(artifactId, groupId, version, p.toAbsolutePath().toString())).toList();
    }

    public void copyDependenciesWithRemoval(String basePath, String repoBasePath, ImportArtifact pomFile) throws Exception {
        var result = copyDependencies(basePath, pomFile);
        var output = result.a;
        var isSuccess = result.b;
        if (isSuccess) {
            return;
        }
        var removeList = PomUtils.extractDependencyFromError(output);
        var pomFileList = PomUtils.findPomFiles(repoBasePath);
        for (File file : pomFileList) {
            for (ImportArtifact remove: removeList) {
                PomUtils.removeDependency(file.getAbsolutePath(), remove.getGroupId(), remove.getArtifactId());
            }
        }

        result = copyDependencies(basePath, pomFile);
        isSuccess = result.b;
        if (!isSuccess) {
            throw new Exception("Failed to copy dependencies: " + result.a);
        }
    }

    public Pair<List<String>, Boolean> copyDependencies(String basePath, ImportArtifact pomFile) throws Exception {
        var outPutDir = "-DoutputDirectory=" + basePath + "/dependencies";
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("mvn", "dependency:copy-dependencies", outPutDir, "-Dmdep.useRepositoryLayout=true", "-f", pomFile.getArtifactPath());
        processBuilder.directory(new File(basePath));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;

        List<String> lines = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            if (line.contains("[ERROR]")) {
                lines.add(line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            return new Pair<>(lines, false);
        }
        return new Pair<>(lines, true);
    }

    public void copyProjectArtifact(String basePath, ImportArtifact projectArtifact) throws Exception {
        var outPutDir = "-DoutputDirectory=" + basePath;
        var artifact = "-Dartifact=" + projectArtifact;
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("mvn", "-U", "dependency:copy", outPutDir, artifact);
        processBuilder.directory(new File(basePath));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;

        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new Exception("Error while copying project artifact");
        }
    }

    public void install(String url, String destination) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("curl", "-O", url);
        processBuilder.directory(new File(destination));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Capture the output using BufferedReader
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;

        System.out.println("Output of pwd command:");
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        // Wait for the process to complete and get the exit code
        int exitCode = process.waitFor();
        System.out.println("Process exited with code: " + exitCode);
    }

    public Pair<ImportArtifact, Integer> install(ImportArtifact importArtifact, String destination, Boolean isPomFile, Boolean withPathExtender) throws IOException, InterruptedException {
        var groupId = importArtifact.getGroupId();
        var artifactId = importArtifact.getArtifactId();
        var version = importArtifact.getVersion();
        var installUrl = getInstallUrl(groupId, artifactId, version);
        if (isPomFile) {
            installUrl = getInstallPomUrl(groupId, artifactId, version);
        }
        File directory;
        if (withPathExtender) {
            directory = new File(destination + File.separator + groupId);
        } else {
            directory = new File(destination);
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("curl", "-O", installUrl);
        processBuilder.directory(directory);
        processBuilder.redirectErrorStream(true);
        String fileDestination;
        if (withPathExtender) {
            fileDestination =  destination + File.separator + groupId + File.separator + artifactId + "-" + version + (isPomFile ? ".pom" : ".jar");
        } else {
            fileDestination =  destination + File.separator + artifactId + "-" + version + (isPomFile ? ".pom" : ".jar");
        }
        System.out.println(fileDestination);
        importArtifact.setArtifactPath(fileDestination);
        File file = new File(fileDestination);

        if (!file.exists()) {
            directory.mkdirs();
            Process process = processBuilder.start();

            // Capture the output using BufferedReader
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            System.out.println("Output of pwd command:");
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // Wait for the process to complete and get the exit code
            int exitCode = process.waitFor();
            System.out.println("Process exited with code: " + exitCode);
            return new Pair(importArtifact, exitCode);
        }
        return new Pair<>(importArtifact, 0);
    }

    public static String getMetaDataUrl(ImportArtifact importArtifact) {
        String groupId = importArtifact.getGroupId();
        String artifactId = importArtifact.getArtifactId();
        return "https://repo.maven.apache.org/maven2/"
                + groupId.replace(".", "/") + "/" + artifactId.replace(".", "/") + "/maven-metadata.xml";
    }

    public static List<String> fetchMetadata(ImportArtifact projectArtifact) throws Exception {
        var metadataUrl = getMetaDataUrl(projectArtifact);
        List<String> result = new ArrayList<>();
        URL url = new URL(metadataUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("<version>")) {
                    result.add(line.replace("<version>", "").replace("</version>", "").trim());
                }

            }
        }
        return result.reversed();
    }

    private static int minDisRec(String s1, String s2, int m, int n, int[][] memo) {

        if (m == 0) return n;

        if (n == 0) return m;

        if (memo[m][n] != -1) return memo[m][n];

        if (s1.charAt(m - 1) == s2.charAt(n - 1)) {
            memo[m][n] = minDisRec(s1, s2, m - 1, n - 1, memo);
        } else {

            int insert = minDisRec(s1, s2, m, n - 1, memo);    // Insert
            int remove = minDisRec(s1, s2, m - 1, n, memo);    // Remove
            int replace = minDisRec(s1, s2, m - 1, n - 1, memo); // Replace
            memo[m][n] = 1 + Math.min(insert, Math.min(remove, replace));
        }

        return memo[m][n]; // Return the computed minimum distance
    }

    // Function to initialize memoization table and start the recursive function
    public static int minDis(String s1, String s2) {
        int m = s1.length(), n = s2.length();
        int[][] memo = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) {
            for (int j = 0; j <= n; j++) {
                memo[i][j] = -1;
            }
        }
        return minDisRec(s1, s2, m, n, memo);
    }

    public static String findNearest(String input, List<String> compare) {
        if (input == null) return compare.getFirst();
        if (compare.contains(input)) return input;
        for (String c : compare) {
            if (c.contains(input) || input.contains(c)) return c;
        }
        String baseInput = input.replaceAll("[^0-9.]", "");
        List<String> filteredCompare = compare.stream().map(c -> c.replaceAll("[^0-9.]", "")).toList();
        String nearest = filteredCompare.stream()
                .min(Comparator.comparingInt(s -> minDis(baseInput, s)))
                .orElse(null);

        if (nearest != null) {
            int lowestScore = minDis(baseInput, nearest);
            int threshold = baseInput.length() / 2;

            if (lowestScore > threshold) {
                return compare.getFirst();
            }
        }
        return compare.get(filteredCompare.indexOf(nearest));
    }
}
