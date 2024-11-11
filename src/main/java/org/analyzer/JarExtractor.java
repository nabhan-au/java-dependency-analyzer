package org.analyzer;

import java.io.*;
import java.util.jar.*;
import java.util.Enumeration;

public class JarExtractor {
    public static void extract(String jarFilePath, String outputDirPath) {
        try (JarFile jarFile = new JarFile(jarFilePath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                File outputFile = new File(outputDirPath, entry.getName());

                if (entry.isDirectory()) {
                    // If the entry is a directory, create it
                    outputFile.mkdirs();
                } else {
                    // If the entry is a file, extract it
                    File parentDir = outputFile.getParentFile();
                    if (parentDir != null) {
                        parentDir.mkdirs(); // Ensure parent directories are created
                    }

                    try (InputStream inputStream = jarFile.getInputStream(entry);
                         FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }

            System.out.println("Extraction completed successfully!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
