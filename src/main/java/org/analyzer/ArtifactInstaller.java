package org.analyzer;

import org.analyzer.models.ImportArtifact;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class ArtifactInstaller {
    private String repositoryPath = "https://repo1.maven.org/maven2/com/google/http-client/google-http-client/1.45.1/google-http-client-1.45.1.jar";

    public String getInstallUrl(String groupId, String artifactId, String version) {
        groupId = groupId.replace('.', '/');
        return "https://repo1.maven.org/maven2/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
    }

    public ImportArtifact install(ImportArtifact importArtifact, String destination) throws IOException, InterruptedException {
        var groupId = importArtifact.getGroupId();
        var artifactId = importArtifact.getArtifactId();
        var version = importArtifact.getVersion();
        var installUrl = getInstallUrl(groupId, artifactId, version);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("curl", "-O", installUrl);
        processBuilder.directory(new File(destination));
        processBuilder.redirectErrorStream(true);
        var fileDestination =  destination + "/" + artifactId + "-" + version + ".jar";
        importArtifact.setArtifactLocation(fileDestination);
        File file = new File(fileDestination);

        if (!file.exists()) {
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

        return importArtifact;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ArtifactInstaller installer = new ArtifactInstaller();
        installer.install(new ImportArtifact("us.ihmc", "ihmc-perception", "0.14.0-241016"), "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/temp-repo");
    }
}
