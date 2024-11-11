package org.analyzer;

import java.io.IOException;

public class FernflowerDecompilerRunner {
    static String fernflowerJarPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/java-dependency-analyzer/java-decompiler.jar";

    public static void decompile(String inputJarPath, String outputDirPath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java", "-jar", fernflowerJarPath, inputJarPath, outputDirPath
            );
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("Decompile failed with exit code: " + exitCode);
            } else {
                System.out.println("Decompile completed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
