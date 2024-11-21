package org.analyzer;

import com.github.javaparser.utils.Pair;
import org.analyzer.models.ImportArtifact;
import org.openqa.selenium.By;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MavenArtifactCrawler {

    public static List<String> getVersionsFromMavenRepo(ImportArtifact importArtifact) {
        var groupId = importArtifact.getGroupId();
        var artifactId = importArtifact.getArtifactId();
        // Set up ChromeOptions (optional)
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        // Uncomment the following line to run in headless mode (no GUI)

        // Initialize WebDriver
        WebDriver driver = new ChromeDriver(options);
        List<String> versions = new ArrayList<>();
        try {
            // Navigate to the URL
            String url = "https://mvnrepository.com/artifact/" + groupId + "/" + artifactId;
            driver.get(url);

            // Wait until elements with class 'vbtn release' are present
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.cssSelector(".vbtn.release")));

            // Locate all elements with class 'vbtn release'
            List<WebElement> elements = driver.findElements(By.cssSelector(".vbtn.release"));

            // Iterate over the elements and print their values
            for (WebElement element : elements) {
                String value = element.getText();
                versions.add(value);
            }

//            List<WebElement> aElement = driver.findElements(By.cssSelector("a.vbtn"));
//            for (WebElement element : aElement) {
//                // Get the visible text of the element
//                String text = element.getText().trim();
//                // Since the text may include the size (e.g., "pom (1 KB)"), we need to extract "pom"
//                System.out.println(text);
//            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Close the browser
            driver.quit();
        }
        System.out.println("Found versions for " + importArtifact + ": " + versions);
        return versions;
    }

    public static String getJarUrlFromMaven(ImportArtifact importArtifact) {
        var groupId = importArtifact.getGroupId();
        var artifactId = importArtifact.getArtifactId();
        var version = importArtifact.getVersion();
        // Set up ChromeOptions (optional)
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        // Uncomment the following line to run in headless mode (no GUI)

        // Initialize WebDriver
        WebDriver driver = new ChromeDriver(options);
        try {
            // Navigate to the URL
            String url = "https://mvnrepository.com/artifact/" + groupId + "/" + artifactId + "/" + version;
            System.out.println("Searching: " + url);
            driver.get(url);

            // Wait until elements with class 'vbtn release' are present
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.cssSelector("a.vbtn")));

            List<WebElement> aElement = driver.findElements(By.cssSelector("a.vbtn"));
            for (WebElement element : aElement) {
                // Get the visible text of the element
                String text = element.getText().trim();
                if (text.startsWith("jar")) {
                    String href = element.getAttribute("href");
                    System.out.println("Found url for " + importArtifact + ": " + href);
                    return href;
                }
            }

            for (WebElement element : aElement) {
                // Get the visible text of the element
                String text = element.getText().trim();
                if (text.startsWith("pom")) {
                    String href = element.getAttribute("href");
                    System.out.println("Found url for " + importArtifact + ": " + href);
                    return href;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            // Close the browser
            driver.quit();
        }
        throw new RuntimeException("Could not find url for " + importArtifact);
    }

    public static Pair<String, String> getExistingVersion(ImportArtifact artifact, String repoUrl, List<String> allPossibleVersions) {
        var startIndex = allPossibleVersions.indexOf(artifact.getVersion());
        if (startIndex == -1) {
            System.out.println("Could not find version for " + artifact);
            return new Pair<>("", artifact.getVersion());
        }
        var currentIndex = startIndex;
        var reachFirst = false;
        var isFound = false;
        while (!isFound) {
            if (currentIndex == allPossibleVersions.size()) {
                System.out.println("Could not find version for " + artifact);
                return new Pair<>("", artifact.getVersion());
            }
            if (currentIndex == -1) {
                reachFirst = true;
                currentIndex = startIndex + 1;
            }

            System.out.println("Checking: " + allPossibleVersions.get(currentIndex));
            isFound = isArtifactExist(repoUrl, new ImportArtifact(artifact.getArtifactId(), artifact.getGroupId(), allPossibleVersions.get(currentIndex)));
            if (isFound) {
                break;
            } else {
                try {
                    var currentArtifact = new ImportArtifact(artifact.getArtifactId(), artifact.getGroupId(), allPossibleVersions.get(currentIndex));
                    var url = getJarUrlFromMaven(currentArtifact);
                    if (url.endsWith(".pom")) {
                        return new Pair<>(extractRepoUrl(currentArtifact, url), currentArtifact.getVersion());
                    }
                    System.out.println("Checking maven path: " + artifact.getArtifactId() + ": " + url);
                    if (isArtifactExist(extractRepoUrl(currentArtifact, url), currentArtifact)) {
                        return new Pair<>(extractRepoUrl(currentArtifact, url), currentArtifact.getVersion());
                    }

                } catch (Exception e) {
                    System.out.println("Could not find url for " + artifact);
                }
            }

            if (reachFirst) {
                currentIndex++;
            } else {
                currentIndex--;
            }
        }
        return new Pair<>("", allPossibleVersions.get(currentIndex));
    }

    public static boolean isArtifactExist(String repositoryUrl, ImportArtifact artifact) {
        var groupId = artifact.getGroupId();
        var artifactId = artifact.getArtifactId();
        var version = artifact.getVersion();
        String artifactPath = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/"
                + artifactId + "-" + version + ".jar";
        String fullUrl = repositoryUrl + "/" + artifactPath;

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() == 200) {
                System.out.println("Artifact found: " + fullUrl);
                return true;
            } else {
                System.out.println("Artifact not found: " + fullUrl + " (HTTP " + response.statusCode() + ")");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error checking artifact at: " + fullUrl);
            return false;
        }
    }

    public static String extractRepoUrl(ImportArtifact artifact, String artifactUrl) {
        // Convert groupId to path by replacing '.' with '/'
        String groupPath = artifact.getGroupId().replace('.', '/');

        // Construct the artifact path relative to the repository
        String artifactPath = groupPath + "/" + artifact.getArtifactId() + "/" + artifact.getVersion();

        // Find the index of the artifact path in the artifact URL
        int index = artifactUrl.indexOf(artifactPath);

        if (index != -1) {
            // Extract the base repository URL up to the artifact path
            String repoUrl = artifactUrl.substring(0, index - 1); // Subtract 1 to remove the trailing '/'
            return repoUrl;
        } else {
            // Artifact path not found in the URL
            throw new IllegalArgumentException("Artifact path not found in the provided URL.");
        }
    }

    public static void main(String[] args) {
        String groupId = "jgraph";
        String artifactId = "jgraph";
        System.out.println(MavenArtifactCrawler.getExistingVersion(new ImportArtifact(artifactId, groupId, "5.0.3"), "https://repo1.maven.org/maven2", new ArrayList<>(Arrays.asList("5.0.7","5.0.6","5.0.4","5.0.3","5.0.2"))));
//        System.out.println(MavenArtifactCrawler.getJarUrlFromMaven(new ImportArtifact(artifactId, groupId, "0.3.3")));


            // Fetch the HTML content of the page
//            Document doc = connection.get();
//
//
//            // Select the table that contains the versions
//            Elements tables = doc.select("table.grid.versions");
//
//            List<String> versions = new ArrayList<>();
//
//            if (!tables.isEmpty()) {
//                Element versionsTable = tables.first();
//                // Select all rows in the table
//                Elements rows = versionsTable.select("tr");
//                for (Element row : rows) {
//                    // Select the cell that contains the version number
//                    Element versionCell = row.selectFirst("a.vbtn.release");
//                    if (versionCell != null) {
//                        String version = versionCell.text().trim();
//                        versions.add(version);
//                    }
//                }
//            }
//
//            // Print the list of versions
//            System.out.println("Available Versions:");
//            for (String version : versions) {
//                System.out.println(version);
//            }
    }
}