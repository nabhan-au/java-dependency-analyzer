package org.analyzer.models;

public class ImportClassPath {
    private String path;
    private String variable;
    private String originalPath;
    private Boolean isStatic;
    private Boolean isWildCard;

    public ImportClassPath(String path) {
        this(path, path);
    }
    public ImportClassPath(String path, String originalPath) {
        this.originalPath = originalPath;
        this.isStatic = false;
        this.isWildCard = false;
        this.variable = "";

        path = path.replace(";", "");
        if (path.endsWith(".*")) {
            path = path.substring(0, path.length() - 2);
            this.isWildCard = true;
            this.variable = "*";
        }

        if (path.contains("import static")) {
            path = path.replace("import static ", "");
            this.isStatic = true;
        } else {
            path = path.replace("import ", "");
        }

        this.path = path;
    }
    public String getOriginalPath() { return originalPath; }

    public String getPath() {
        return path;
    }

    public String getVariable() {
        return variable;
    }

    public Boolean isStatic() {
        return isStatic;
    }

    public Boolean isWildCard() {
        return isWildCard;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setVariable(String variable) {
        this.variable = variable;
    }

    @Override
    public String toString() {
        var temppath = path;

        if (isStatic) {
            temppath = "static " + path;
        }
        if (isWildCard) {
            temppath = temppath + ".*";
        } else if (!variable.isEmpty()) {
            temppath = temppath + "." + variable;
        }
        return "import " + temppath + ";";
    }
}
