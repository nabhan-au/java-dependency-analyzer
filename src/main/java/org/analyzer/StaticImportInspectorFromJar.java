package org.analyzer;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.github.javaparser.utils.Pair;
import org.analyzer.models.ImportClassPath;
import org.analyzer.models.ImportDetails;

public class StaticImportInspectorFromJar {

    private final ClassLoader classloader;
    private Map<String, List<String>> getAllClassPathFromJarFileCache = new HashMap<>();

    public StaticImportInspectorFromJar(List<File> jarFile) throws MalformedURLException {
        List<URL> urls = new ArrayList<>();
        for (File file : jarFile) {
            urls.add(new URL("jar:file:" + file.getAbsolutePath() + "!/"));
        }
        URL[] arrayUrl = urls.toArray(URL[]::new);
        classloader = URLClassLoader.newInstance(arrayUrl);
    }

    public Pair<Class<?>, Queue<String>> findClassWithPath(String className) {
        try {
            var clazz = classloader.loadClass(className);
            return new Pair<>(clazz, new LinkedList<>());
        } catch (ClassNotFoundException e) {
            // Split into outer class path and current inner class name
            int lastDotIndex = className.lastIndexOf('.');
            if (lastDotIndex == -1) {
                return new Pair<>(null, new LinkedList<>());
            }
            String outerClassPath = className.substring(0, lastDotIndex);
            String currentInnerClassName = className.substring(lastDotIndex + 1);
            var result = findClassWithPath(outerClassPath);
            result.b.add(currentInnerClassName);
            return new Pair<>(result.a, result.b);
        }
    }

    public Class<?> resolveClassWithPath(Class<?> mainClass, Queue<String> path) throws ClassNotFoundException {
        var currentClass = mainClass;
        var isClassPresent = false;
        while (!path.isEmpty()) {
            String className = path.poll();
            Class<?>[] innerClass = currentClass.getClasses();
            for (var i = 0; i < innerClass.length; i++) {
                if (className.equals(innerClass[i].getSimpleName())) {
                    currentClass = innerClass[i];
                    isClassPresent = true;
                }
            }
            if (!isClassPresent) {
                throw new ClassNotFoundException(currentClass.getName() + " finding: " + className);
            }
        }
        return currentClass;
    }

    public Class<?> loadClass(String className) throws ClassNotFoundException {
        var result = findClassWithPath(className);
        if (result.a == null) {
            throw new ClassNotFoundException(className);
        }
        return resolveClassWithPath(result.a, result.b);
    }

    public Pair<ImportDetails, List<ImportClassPath>> getImportDetailsWithMultipleClassPath(List<ImportClassPath> classPathList) {
        ImportDetails importDetails = new ImportDetails();
        List<ImportClassPath> unableToGetClassList = new ArrayList<>();

        // Check is a path point to Class or Inner Class and return Class object
        getImportDetailsFromClassPath(classPathList, importDetails, unableToGetClassList);

        // Check is current path is a package and add import from package to result
        List<ImportClassPath> unableToGetClassesInPackage = new ArrayList<>();
        var classPathInPackage = getClassPathFromPackage(unableToGetClassList, unableToGetClassesInPackage);
        getImportDetailsFromClassPath(classPathInPackage, importDetails, unableToGetClassesInPackage);

        // Check is path belong to java build in library (for non-static wild card import only)
        List<ImportClassPath> unableToGetClassesFromBuildInLibrary = new ArrayList<>();
        for (ImportClassPath classPath : unableToGetClassesInPackage) {
            if (!classPath.isStatic() && classPath.isWildCard()) {
                try {
                    var classFromBuildInLibrary = getClassPathFromBuildInLibrary(classPath, unableToGetClassesFromBuildInLibrary);
                    getImportDetailsFromClassPath(classFromBuildInLibrary, importDetails, unableToGetClassesFromBuildInLibrary);
                } catch (ClassNotFoundException e) {
                    unableToGetClassesFromBuildInLibrary.add(classPath);
                }
            } else {
                unableToGetClassesFromBuildInLibrary.add(classPath);
            }

        }

        return new Pair<>(importDetails, unableToGetClassesFromBuildInLibrary);
    }

    private List<ImportClassPath> getClassPathFromPackage(List<ImportClassPath> unableToGetClassList, List<ImportClassPath> unableToGetClassesInPackage) {
        var classPathInPackage = unableToGetClassList.stream().flatMap(classPath -> {
            var tempPath = classPath.getPath().replaceAll("\\.", "/");
            try {
                var classes = getClassInPackage(tempPath, classPath);
                if (classes.isEmpty()) {
                    unableToGetClassesInPackage.add(classPath);
                }
                return classes.stream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                unableToGetClassesInPackage.add(classPath);
                return null;
            }
        }).toList();
        return classPathInPackage;
    }

    private void getImportDetailsFromClassPath(List<ImportClassPath> classPathList, ImportDetails importDetails, List<ImportClassPath> unableToGetClassList) {
        classPathList.forEach(classPath -> {
            try {
                ImportDetails classImportDetails = getImportDetails(classPath);
                importDetails.methodList.addAll(classImportDetails.methodList);
                importDetails.fieldList.addAll(classImportDetails.fieldList);
                importDetails.classList.addAll(classImportDetails.classList);
            } catch (ClassNotFoundException e) {
                unableToGetClassList.add(classPath);
            }
        });
    }

    public ImportDetails getImportDetails(ImportClassPath classPath) throws ClassNotFoundException {
        var result = getImportDetailsFromJar(classPath);
        return result;
    }

    public ImportDetails getImportDetailsFromJar(ImportClassPath classPath) throws ClassNotFoundException {
        if (!classPath.isStatic()) {
            if (classPath.isWildCard()) {
                var clazz = loadClass(classPath.getPath());
                return new ImportDetails(classPath, new Method[]{}, new Field[]{}, clazz.getClasses());
            } else {
                var clazz = loadClass(classPath.getPath());;
                return new ImportDetails(classPath, new Method[]{}, new Field[]{}, new Class[]{clazz});
            }
        } else {
            if (classPath.isWildCard()) {
                var clazz = loadClass(classPath.getPath());
                List<Method> staticMethods = new ArrayList<>();
                List<Field> staticFields = new ArrayList<>();
                for (Method method : clazz.getMethods()) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        staticMethods.add(method);
                    }
                }
                for (Field field : clazz.getFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        staticFields.add(field);
                    }
                }
                return new ImportDetails(classPath, staticMethods.toArray(Method[]::new), staticFields.toArray(Field[]::new), new Class[]{});
            } else {
                var splitClasspath = classPath.getPath().split("\\.");
                var shortClasspath = Arrays.copyOfRange(splitClasspath, 0, splitClasspath.length - 1);
                var variable = splitClasspath[splitClasspath.length - 1];
                classPath.setPath(String.join(".", shortClasspath));
                classPath.setVariable(variable);
                var clazz = loadClass(classPath.getPath());
                ImportClassPath finalClassPath = classPath;
                var filteredMethod = Arrays.stream(clazz.getMethods()).filter(method ->
                        method.getName().equals(finalClassPath.getVariable())
                ).toArray(Method[]::new);
                var filteredFields = Arrays.stream(clazz.getFields()).filter(field ->
                        field.getName().equals(finalClassPath.getVariable())
                ).toArray(Field[]::new);
                return new ImportDetails(classPath, filteredMethod, filteredFields, new Class[]{});
            }
        }
    }

    public List<ImportClassPath> getClassPathFromBuildInLibrary(ImportClassPath importClassPath, List<ImportClassPath> unableImport) throws ClassNotFoundException {
        List<String> baseModuleClassNames = new ArrayList<>();
        var classPath = importClassPath.getPath().replaceAll("\\.", "/");
        try {
            var moduleReference = ModuleFinder.ofSystem().findAll().stream()
                    .filter(module -> module.descriptor().name().equals("java.base"))
                    .findFirst();
            if (moduleReference.isEmpty()) {
                throw new Exception("Cannot retrieve module reference");
            }
            List<String> classes = moduleReference.get().open().list().toList();
            classes.stream()
                    .filter(name -> {
                        var tempName = name.replaceAll("\\$", "/");
                        if (!tempName.endsWith(".class") || !tempName.startsWith(classPath)) {
                            return false;
                        }
                        tempName = tempName.replace(".class", "");
                        return tempName.split("/").length == classPath.split("/").length + 1;
                    })
                    .forEach(name -> baseModuleClassNames.add(name.replace('/', '.').replace(".class", "")));
        } catch (Exception e) {
            unableImport.add(importClassPath);
        }
        return baseModuleClassNames.stream().map(b -> new ImportClassPath(b, importClassPath.toString())).toList();
    }

    public List<ImportClassPath> getClassInPackage(String packagePath, ImportClassPath classPath) throws IOException, ClassNotFoundException {
        List<ImportClassPath> classPathList = new ArrayList<>();
        // Get the resource URLx
        try {
            Enumeration<URL> resources = classloader.getResources(packagePath);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if (resource.getProtocol().equals("jar")) {

                    // Extract the JAR file path
                    String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
                    try (JarFile jarFile = new JarFile(jarPath)) {
                        String finalPackagePath = packagePath;
                        jarFile.stream()
                                .filter(entry -> isClassInPackage(entry, finalPackagePath))
                                .forEach(entry -> {
                                    // Convert JAR entry to class name
                                    String className = entry.getName()
                                            .replace("/", ".")
                                            .replace(".class", "");
                                    if (classPath == null) {
                                        classPathList.add(new ImportClassPath(className));
                                    } else {
                                        classPathList.add(new ImportClassPath(className, classPath.toString()));
                                    }

                                });
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classPathList;
    }

    public List<String> getAllClassPathFromJarFile(String artifactPath) throws Exception {
        if (getAllClassPathFromJarFileCache.containsKey(artifactPath)) {
            return getAllClassPathFromJarFileCache.get(artifactPath);
        }
        ProcessBuilder processBuilder = new ProcessBuilder();

        processBuilder.command("jar", "tf", Arrays.stream(artifactPath.split("/")).toList().getLast());
        processBuilder.directory(new File(artifactPath.substring(0, artifactPath.lastIndexOf("/"))));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Capture the output of the command
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        List<String> output = new ArrayList<>();
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.trim().endsWith(".class")) {
                output.add(line);
            }
        }

        // Wait for the process to finish
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.out.println(artifactPath + " exited with code " + exitCode);
            System.err.println("jar tf command failed with exit code: " + exitCode);
            throw new Exception("jar tf command failed with exit code: " + exitCode);
        }

        getAllClassPathFromJarFileCache.put(artifactPath, output);
        return output;
    }

    private static boolean isClassInPackage(JarEntry entry, String packagePath) {
        String name = entry.getName();
        return name.startsWith(packagePath + "/") && name.endsWith(".class") && name.indexOf('$') == -1
                && name.substring(packagePath.length() + 1).indexOf('/') == -1; // Only classes at this level
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        File jarFilePath = new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar");
        String fullyQualifiedClassName = args.length > 1 ? args[1] : "import org.bytedeco.cuda.global.nvcomp.*;";
        String fullyQualifiedClassName2 = "import static org.bytedeco.opencl.global.OpenCL.CL_SUCCESS;";
        String fullyQualifiedClassName3 = "import org.bytedeco.javacv.*";

        var staticImport = new StaticImportInspectorFromJar(new ArrayList<>(Arrays.asList(jarFilePath)));
        var result = staticImport.findClassWithPath("org.bytedeco.spinnaker.global.Spinnaker_C");
        var result2 = staticImport.resolveClassWithPath(result.a, result.b);
        System.out.println(result2);
//        var packageImport = staticImport.getClassesInPackage("org/bytedeco/javacv");
//        var result = staticImport.getImportDetailsWithMultipleClassPath(new ArrayList<>(Arrays.asList(new ImportClassPath(fullyQualifiedClassName), new ImportClassPath(fullyQualifiedClassName2)))).a;
//
//        for (SingleImportDetails<Method> method : result.methodList) {
//            if (Modifier.isStatic(method.importObject.getModifiers())) {
//                System.out.println(method.importObject);
//            }
//        }
//        for (SingleImportDetails<Field> field: result.fieldList) {
//            if (Modifier.isStatic(field.importObject.getModifiers())) {
//                System.out.println(field.importObject);
//            }
//        }
//        for (SingleImportDetails<Class<?>> clazz: result.classList) {
//            System.out.println(clazz.importObject);
//        }

//        try {
//            // Load the class from the JAR file
//            URL jarUrl = new URL("jar:file:" + jarFilePath + "!/");
//            URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{jarUrl});
//            Class<?> clazz = classLoader.loadClass(fullyQualifiedClassName);
//
//            System.out.println("Methods available in the class:");
//            System.out.println(clazz);
//
//            for (Class<?> innerClass : clazz.getDeclaredClasses()) {
//
//            }
//
//            clazz.getEnumConstants();
//            clazz.getRecordComponents();
//            clazz.getAnnotations();
//            clazz.getInterfaces();
//            clazz.getAnnotatedInterfaces();
//            clazz.getAnnotatedSuperclass();
//            clazz.getFields();
//            clazz.getMethods();
//
//            for (Method method : clazz.getDeclaredMethods()) {
//                System.out.println("Method: " + method.getName());
//            }
//
//            System.out.println("\nFields available in the class:");
//            for (Field field : clazz.getDeclaredFields()) {
//                if (Modifier.isStatic(field.getModifiers())) {
//                    System.out.println("Field: " + field.getName());
//                }
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }
}

