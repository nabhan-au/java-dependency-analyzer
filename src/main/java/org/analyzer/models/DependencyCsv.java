package org.analyzer.models;

import com.opencsv.bean.CsvBindByName;

public class DependencyCsv {
    @CsvBindByName(column = "artifact_id")
    public String artifactId;
    @CsvBindByName(column = "release_id")
    public String releaseId;
    @CsvBindByName(column = "version")
    public String version;
    @CsvBindByName(column = "timestamp")
    public String timestamp;
    @CsvBindByName(column = "popularity")
    public Integer popularity;
    @CsvBindByName(column = "all_dependency")
    public Integer allDependency;
    @CsvBindByName(column = "compile_dependency")
    public Integer compileDependency;
    @CsvBindByName(column = "provided_dependency")
    public Integer providedDependency;
    @CsvBindByName(column = "dependency")
    public String[] dependency;
}
