package org.analyzer.models;

public class SingleImportDetails<T> {
    public ImportClassPath classPath;
    public ImportClassPath originalClassPath;
    public T importObject;

    public SingleImportDetails(ImportClassPath importClassPath, T importObject) {
        this.classPath = importClassPath;
        this.importObject = importObject;
    }
}
