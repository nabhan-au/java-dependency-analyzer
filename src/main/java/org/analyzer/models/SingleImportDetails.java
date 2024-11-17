package org.analyzer.models;

public class SingleImportDetails<T> {
    public ImportClassPath classPath;
    public T importObject;

    public SingleImportDetails(ImportClassPath importClassPath, T importObject) {
        this.classPath = importClassPath;
        this.importObject = importObject;
    }
}
