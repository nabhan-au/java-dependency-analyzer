package org.analyzer;

import java.io.File;
import java.io.IOException;

public class GitUtils {
    public static void gitCheckoutBranch(String repoPath, String branchName) throws IOException, InterruptedException {
        if (branchName == null || branchName.isEmpty()) {
            return;
        }
        ProcessBuilder processBuilder = new ProcessBuilder("git", "checkout", branchName);
        processBuilder.directory(new java.io.File(repoPath));
        processBuilder.redirectErrorStream(true); // Combine standard error with standard output

        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            System.out.println("Successfully switched to branch: " + branchName);
        } else {
            throw new IOException("Error switching to branch: " + branchName);
        }
    }

    public static String cloneGitRepository(String owner, String repoName, String targetDir)
            throws IOException, InterruptedException {
        // Validate input
        if (owner == null || owner.isEmpty() || repoName == null || repoName.isEmpty()) {
            throw new IllegalArgumentException("Owner and repository name must not be null or empty.");
        }

        // Construct the repository URL
        String repoUrl = "https://github.com/" + owner + "/" + repoName + ".git";

        // Start building the Git clone command
        StringBuilder command = new StringBuilder("git clone");

        // Add the repository URL
        command.append(" ").append(repoUrl);

        // Add target directory if specified
        if (targetDir != null && !targetDir.isEmpty()) {
            command.append(" ").append(targetDir);
        }

        // Execute the command using ProcessBuilder
        ProcessBuilder processBuilder = new ProcessBuilder(command.toString().split(" "));
        processBuilder.directory(new File(".")); // Set the working directory (default is current directory)
        processBuilder.inheritIO(); // Redirect output and error streams to the console

        Process process = processBuilder.start(); // Start the process
        int exitCode = process.waitFor(); // Wait for the process to complete

        if (exitCode == 0 || exitCode == 128) {
            System.out.println("Repository cloned successfully.");
            // Return the full path to the cloned repository
            return new File(targetDir).getAbsolutePath();
        } else {
            throw new RuntimeException("Failed to clone the repository. Exit code: " + exitCode);
        }
    }
}
