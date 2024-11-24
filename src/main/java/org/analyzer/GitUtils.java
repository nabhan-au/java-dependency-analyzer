package org.analyzer;

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
}
