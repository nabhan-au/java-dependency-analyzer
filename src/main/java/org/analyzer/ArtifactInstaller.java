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

public class ArtifactInstaller {
    private String repositoryPath = "https://repo1.maven.org/maven2/com/google/http-client/google-http-client/1.45.1/google-http-client-1.45.1.jar";

    public String getInstallUrl(String groupId, String artifactId, String version) {
        groupId = groupId.replace('.', '/');
        return "https://repo1.maven.org/maven2/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
    }

    public String getInstallPomUrl(String groupId, String artifactId, String version) {
        groupId = groupId.replace('.', '/');
        return "https://repo1.maven.org/maven2/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".pom";
    }

    public Pair<ImportArtifact, Integer> install(ImportArtifact importArtifact, String destination, Boolean isPomFile) throws IOException, InterruptedException {
        var test = "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-dependency-plugin:3.7.0:get (default-cli) on project standalone-pom: Couldn't download artifact: org.eclipse.aether.resolution.DependencyResolutionException: The following artifacts could not be resolved: net.java.dev.jna:jna:jar:5.15.0-SNAPSHOT (absent), com.microsoft.onnxruntime:onnxruntime_gpu:jar:1.11.0 (absent), org.ros.rosjava_bootstrap:message_generation:jar:0.3.3 (absent), org.openjfx:javafx-graphics:jar:linux:17.0.9 (absent), org.apache.commons:com.springsource.org.apache.commons.io:jar:1.4.0 (absent), org.ros.rosjava_core:rosjava:jar:0.2.1 (absent), org.ros.rosjava_messages:std_msgs:jar:0.5.10 (absent), org.ros.rosjava_messages:std_srvs:jar:1.11.1 (absent), org.ros.rosjava_messages:people_msgs:jar:1.0.4 (absent), org.ros.rosjava_messages:sensor_msgs:jar:1.11.7 (absent), org.ros.rosjava_messages:dynamic_reconfigure:jar:1.5.38 (absent), org.ros.rosjava_messages:multisense_ros:jar:3.4.2 (absent), org.ros.rosjava_messages:rosgraph_msgs:jar:1.11.1 (absent), org.ros.rosjava_messages:geometry_msgs:jar:1.11.7 (absent), org.ros.rosjava_messages:trajectory_msgs:jar:1.11.7 (absent), org.ros.rosjava_messages:nav_msgs:jar:1.11.7 (absent), org.ros.rosjava_messages:tf2_msgs:jar:0.5.9 (absent), org.ros.rosjava_messages:tf:jar:1.10.8 (absent), org.apache.commons:com.springsource.org.apache.commons.codec:jar:1.3.0 (absent), org.apache.commons:com.springsource.org.apache.commons.lang:jar:2.4.0 (absent), org.ros.rosjava_bootstrap:gradle_plugins:jar:0.3.3 (absent), com.github.crykn:kryonet:jar:2.22.7 (absent), com.github.esotericsoftware:jsonbeans:jar:0.9 (absent), org.ros.rosjava_messages:stereo_msgs:jar:1.10.6 (absent), org.ros.rosjava_messages:roscpp:jar:1.11.10 (absent), org.ros.rosjava_messages:actionlib_msgs:jar:1.11.7 (absent), org.ros.rosjava_core:apache_xmlrpc_server:jar:0.2.1 (absent), org.ros.rosjava_core:apache_xmlrpc_client:jar:0.2.1 (absent), org.ros.rosjava_core:apache_xmlrpc_common:jar:0.2.1 (absent), org.apache.commons:com.springsource.org.apache.commons.logging:jar:1.1.1 (absent), org.apache.commons:com.springsource.org.apache.commons.net:jar:2.0.0 (absent), org.apache.commons:com.springsource.org.apache.commons.httpclient:jar:3.1.0 (absent): Could not find artifact net.java.dev.jna:jna:jar:5.15.0-SNAPSHOT";
        var groupId = importArtifact.getGroupId();
        var artifactId = importArtifact.getArtifactId();
        var version = importArtifact.getVersion();
        var installUrl = getInstallUrl(groupId, artifactId, version);
        if (isPomFile) {
            installUrl = getInstallPomUrl(groupId, artifactId, version);
        }
        var directory = new File(destination + File.separator + groupId);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("curl", "-O", installUrl);
        processBuilder.directory(directory);
        processBuilder.redirectErrorStream(true);
        var fileDestination =  destination + File.separator + groupId + File.separator + artifactId + "-" + version + (isPomFile ? ".pom" : ".jar");
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

    public static void main(String[] args) throws Exception {
        var artifact = new ImportArtifact("xmlpull", "xmlpull", "5.15.0-SNAPSHOT");
        var availableArtifact = ArtifactInstaller.fetchMetadata(artifact);
        System.out.println(availableArtifact);

        System.out.println(findNearest(artifact.getVersion(), availableArtifact));
//        ArtifactInstaller installer = new ArtifactInstaller();
//        installer.install(artifact, "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/temp-repo", false);

    }
}
