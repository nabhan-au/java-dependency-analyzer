package org.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.resolution.types.*;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        // zip -d yourjar.jar 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA'

//        mvn org.apache.maven.plugins:maven-dependency-plugin:RELEASE:copy \
//-Dartifact=us.ihmc:ihmc-perception:0.14.0-241016:jar

//        initParser("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar");

        var pathList = getFileList("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java/us/ihmc/perception");

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new JavaParserTypeSolver(new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src")));
        typeSolver.add(new JarTypeSolver("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar"));
        typeSolver.add(new ReflectionTypeSolver());

        ParserConfiguration parserConfig = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);

        StaticJavaParser.setConfiguration(parserConfig);

//        ReflectionTypeSolver reflectionTypeSolver = new ReflectionTypeSolver();
//        ParserConfiguration config = new ParserConfiguration();
//        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
//        StaticJavaParser.setConfiguration(config);

//        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
//        combinedTypeSolver.add(reflectionTypeSolver);
//        combinedTypeSolver.add(new JarTypeSolver("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_jar/ihmc-perception.jar"));
//        combinedTypeSolver.add(new JavaParserTypeSolver("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src"));
//        combinedTypeSolver.add(javaParserTypeSolver);
//        combinedTypeSolver.add(new JavaParserTypeSolver(new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-java-toolkit/src")));

//        for (String s : repoPath) {
//            combinedTypeSolver.add(new JavaParserTypeSolver(new File(s)));
//        }
//
//        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
//        StaticJavaParser.getParserConfiguration().J(symbolSolver);

        CompilationUnit cu = StaticJavaParser.parse(new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java/us/ihmc/perception/demo/NVCompDemo.java"));
        cu.findAll(MethodCallExpr.class).forEach(methodCallExpr -> {
            System.out.println(methodCallExpr.getName() + ":"  + methodCallExpr.getScope());
        });

        cu.findAll(FieldDeclaration.class).forEach(varDeclaration -> {
            varDeclaration.getVariables().forEach(variable -> {
                System.out.println(variable.getName() + ":"  + variable.getType());
            });
        });

        var method = cu.findAll(MethodCallExpr.class).get(2);
        System.out.println(method.getNameAsString());
//        System.out.println(method.resolve().toString());

//        for (Path path: pathList) {
//            CompilationUnit cu = null;
//            try {
//                cu = StaticJavaParser.parse(path.toFile());
//                System.out.println(path.toString());
//                /Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java/us/ihmc/sensors/RealsenseColorDepthImagePublisher.java
//                /Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java/us/ihmc/sensors/ZEDColorDepthImagePublisher.java
//                /Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java/us/ihmc/perception/demo/NVCompDemo.java
//                cu = StaticJavaParser.parse(new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java/us/ihmc/perception/demo/NVCompDemo.java"));
//                var fileImports = getImportsList(cu);
//                var actualImports = getUsingImport(cu);
//                var actualImportsList = new ArrayList<>(actualImports.stream()
//                        .flatMap(model -> extractCompositeTypeInfo(model.getResolvedType(), "").stream()).distinct().toList());
//                actualImports.forEach(model -> actualImportsList.add(model.getImportName()));
//
//
//                var unusedImport =  fileImports.stream()
//                        .filter(fileImport -> isUnused(actualImportsList, fileImport))
//                        .toList();
//
//                var usingImport =  fileImports.stream()
//                        .filter(fileImport -> !isUnused(actualImportsList, fileImport))
//                        .toList();
//
//                unusedImport.forEach(System.out::println);
//                break;
//            } catch (Exception e) {
//                e.printStackTrace();
//                System.out.println("Cannot parse file: " + path);
//            }
//        };
    }

    public static List<Path> getFileList(String repoPath) {
        List<Path> pathList = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.java");
        try (Stream<Path> paths = Files.walk(Paths.get(repoPath))) {
            paths
                    .filter(path -> matcher.matches(path) && Files.isRegularFile(path))
                    .forEach(pathList::add);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return pathList;
    }

    private static Boolean isUnused(List<String> actualImports, ImportDeclaration fileImport) {
        if (fileImport.isAsterisk()) {
            String importName = fileImport.getNameAsString();
            return actualImports.stream().noneMatch(actual -> {
                String[] splitActualImport = actual.split("\\.");
                if (splitActualImport.length <= 1) {
                    return false;
                }
                splitActualImport = Arrays.copyOfRange(splitActualImport, 0, splitActualImport.length - 1);
                return String.join(".",splitActualImport).equals(importName);
            });
        } else {
            return !actualImports.contains(fileImport.getNameAsString());
        }
    }

    private static void debug(List<Model> models) {
        models.forEach(model -> {
            System.out.println("--------------------------------");
            System.out.println(model.getNode());
            System.out.println(model.getImportName());
        });
    }

    private static List<Model> extractType(Node node) {
        List<Model> models = new ArrayList<>();

        switch (node) {
            case LambdaExpr lambdaExpr:
                break;
            case FieldDeclaration fieldDeclaration:
                fieldDeclaration.getVariables().forEach(variable -> {
                    ResolvedValueDeclaration resolvedValue = variable.resolve();
                    extractCompositeTypeInfo(resolvedValue.getType(), "");
                    models.add(new Model(fieldDeclaration, resolvedValue.getType(), resolvedValue.getType().describe(), "Field"));
                });
                break;
            case MethodDeclaration methodDeclaration:
                ResolvedMethodDeclaration resolvedMethodDeclaration = methodDeclaration.resolve();
                models.add(new Model(methodDeclaration, null, resolvedMethodDeclaration.getQualifiedName(), "Method"));
                models.add(new Model(methodDeclaration, resolvedMethodDeclaration.getReturnType(), resolvedMethodDeclaration.getQualifiedName(), "Method Return"));
                break;
            case MethodCallExpr methodCallExpr:
                ResolvedMethodDeclaration resolvedMethodCallDeclaration = methodCallExpr.resolve();
                models.add(new Model(methodCallExpr, null, resolvedMethodCallDeclaration.getQualifiedName(), "Method Call"));
                models.add(new Model(methodCallExpr, resolvedMethodCallDeclaration.getReturnType(), resolvedMethodCallDeclaration.getReturnType().describe(), "Method Return"));
                break;
            case ClassOrInterfaceDeclaration classOrInterfaceDeclaration:
                String className = classOrInterfaceDeclaration.getNameAsString();
                List<ClassOrInterfaceType> extendedTypes = classOrInterfaceDeclaration.getExtendedTypes();

                if (!extendedTypes.isEmpty()) {
                    for (ClassOrInterfaceType extendedType : extendedTypes) {
                        models.add(new Model(classOrInterfaceDeclaration, extendedType.resolve(), extendedType.resolve().describe(), "Class"));
                    }
                } else {
                    System.out.println("Class: " + className + " does not extend any class.");
                }
                break;
            case ObjectCreationExpr objectCreationExpr:
                ResolvedType resolvedObjectType = objectCreationExpr.calculateResolvedType();
                models.add(new Model(objectCreationExpr, resolvedObjectType, resolvedObjectType.describe(), "Object Creation"));
                break;
            case ArrayCreationExpr arrayCreationExpr:
                ResolvedType resolvedArrayType = arrayCreationExpr.calculateResolvedType();
                models.add(new Model(arrayCreationExpr, resolvedArrayType, resolvedArrayType.describe(), "Array Creation"));
                break;
            case CastExpr castExpr:
                ResolvedType castResolvedType = castExpr.calculateResolvedType();
                models.add(new Model(castExpr, castResolvedType, castResolvedType.describe(), "Cast"));
                break;
            case VariableDeclarationExpr variableDeclarationExpr:
                variableDeclarationExpr.getVariables().forEach(variable -> {
                    ResolvedValueDeclaration resolvedValue = variable.resolve();
                    models.add(new Model(variableDeclarationExpr, resolvedValue.getType(), resolvedValue.getType().describe(), "Variable"));
                });
                break;
            case AnnotationExpr annotationExpr:
                ResolvedAnnotationDeclaration resolvedAnnotationType = annotationExpr.resolve();
                models.add(new Model(annotationExpr, null, resolvedAnnotationType.getQualifiedName(), "Annotation"));
                break;
            case Parameter parameter:
                ResolvedType paramType = parameter.getType().resolve();
                models.add(new Model(parameter, paramType, paramType.describe(), "Parameter"));
                break;
            case Expression expr:
                if (!expr.isSwitchExpr()) {
                    ResolvedType resolvedType = expr.calculateResolvedType();
                    models.add(new Model(expr, resolvedType, resolvedType.describe(), "Expression"));
                } else {
                    SwitchExpr switchExpr = (SwitchExpr) expr.calculateResolvedType();
                }
                break;
            default:
        }

        return models;
    }

    private static List<Model> getUsingImport(Node node) {
        // Try to resolve the type if it is an Expression or other resolvable node
        List<Model> models = new ArrayList<>();
        try {
            models.addAll(extractType(node));
//            if (node instanceof FieldDeclaration fieldDeclaration) {
//                fieldDeclaration.getVariables().forEach(variable -> {
//                    ResolvedValueDeclaration resolvedValue = variable.resolve();
//                    models.add(new Model(fieldDeclaration, resolvedValue.getType(), resolvedValue.getType().describe(), "Field"));
//                });
//            } else if (node instanceof MethodDeclaration methodDeclaration) {
//                ResolvedMethodDeclaration resolvedType = methodDeclaration.resolve();
//                models.add(new Model(methodDeclaration, resolvedType.getReturnType(), resolvedType.getQualifiedName(), "Method"));
//            } else if (node instanceof MethodCallExpr methodCallExpr) {
//                ResolvedMethodDeclaration resolvedType = methodCallExpr.resolve();
//                models.add(new Model(methodCallExpr, resolvedType.getReturnType(), resolvedType.getQualifiedName(), "Method Call"));
//            }
//            else if (node instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
//                String className = classOrInterfaceDeclaration.getNameAsString();
//
//                // Step 4: Get the extended types (superclasses)
//                List<ClassOrInterfaceType> extendedTypes = classOrInterfaceDeclaration.getExtendedTypes();
//
//                if (!extendedTypes.isEmpty()) {
//
//                    // Print all extended types
//                    for (ClassOrInterfaceType extendedType : extendedTypes) {
//                        models.add(new Model(classOrInterfaceDeclaration, extendedType.resolve(), extendedType.resolve().describe(), "Class"));
//                    }
//                } else {
//                    System.out.println("Class: " + className + " does not extend any class.");
//                }
//            } else if (node instanceof Expression) {
//                Expression expr = (Expression) node;
//                ResolvedType resolvedType = expr.calculateResolvedType();
//                models.add(new Model(expr, resolvedType, resolvedType.describe(), "Expression"));
//            }
        } catch (Exception e) {
            System.out.println("Unable to resolve type for node: " + node);
            if (node instanceof MethodDeclaration) {
                System.out.println("MethodDeclaration: " + node);
            } else if (node instanceof FieldDeclaration) {
                System.out.println("FieldDeclaration: " + node);
            } else if (node instanceof MethodCallExpr) {
                System.out.println("MethodCallExpr: " + node);
            } else if (node instanceof ClassOrInterfaceDeclaration) {
                System.out.println("ClassOrInterfaceDeclaration: " + node);
            } else if (node instanceof ObjectCreationExpr) {
                System.out.println("ObjectCreationExpr: " + node);
            } else if (node instanceof ArrayCreationExpr) {
                System.out.println("ArrayCreationExpr: " + node);
            } else if (node instanceof VariableDeclarationExpr) {
                System.out.println("VariableDeclarationExpr: " + node);
            } else if (node instanceof AnnotationExpr) {
                System.out.println("AnnotationExpr: " + node);
            } else if (node instanceof Parameter) {
                System.out.println("Parameter: " + node);
            } else if (node instanceof Expression) {
                e.printStackTrace();
                System.out.println(e.getMessage());
                System.out.println("Expression: " + node);
            }
        }

        // Recursively traverse all child nodes
        for (Node child : node.getChildNodes()) {
            models.addAll(getUsingImport(child));
        }

        return models;
    }

    private static List<String> extractCompositeTypeInfo(ResolvedType resolvedType, String indent) {
        List<String> types = new ArrayList<String>();
        if (resolvedType == null) {
            return types;
        }
        if (resolvedType.isReferenceType()) {
            // If the resolved type is a reference type (e.g., class or interface)
            ResolvedReferenceType referenceType = resolvedType.asReferenceType();

            // Print the base type information
            String baseType = referenceType.getQualifiedName();
//            System.out.println(indent + "Base Type: " + baseType);
            types.add(baseType);

            // If the type has type parameters, recursively print each parameter
            if (!referenceType.typeParametersValues().isEmpty()) {
//                System.out.println(indent + "Type Parameters:");
                referenceType.typeParametersValues().forEach(paramType -> {
                    types.addAll(extractCompositeTypeInfo(paramType, indent + "  "));
                });
            }
        } else if (resolvedType.isArray()) {
//            System.out.println(indent + "Array Type: " + resolvedType.describe());
            types.add(resolvedType.describe());
            types.addAll(extractCompositeTypeInfo(resolvedType.asArrayType().getComponentType(), indent + "  "));
        } else if (resolvedType.isWildcard()) {
            // Handle wildcard types (e.g., ?, ? extends Number)
            ResolvedWildcard wildcard = resolvedType.asWildcard();
//            System.out.println(indent + "Wildcard Type: " + wildcard.describe());
            types.add(wildcard.describe());
            if (wildcard.isBounded()) {
//                System.out.println(indent + "  Bound Type:");
                types.addAll(extractCompositeTypeInfo(wildcard.getBoundedType(), indent + "    "));
            }

        } else if (resolvedType.isTypeVariable()) {
            // Handle type variables (e.g., <T> in generics)
            ResolvedTypeVariable typeVariable = resolvedType.asTypeVariable();
//            System.out.println(indent + "Type Variable: " + typeVariable.describe());
            types.add(typeVariable.describe());

        } else if (resolvedType.isUnionType()) {
            // Handle union types (e.g., used in multi-catch blocks)
            ResolvedUnionType unionType = resolvedType.asUnionType();
//            System.out.println(indent + "Union Type: " + unionType.describe());
            types.add(unionType.describe());
            unionType.getElements().forEach(unionElement -> {
                types.addAll(extractCompositeTypeInfo(unionElement, indent + "  "));
            });

        }

        return types;
    }

    private static List<ImportDeclaration> getImportsList(CompilationUnit cu) {
        List<ImportDeclaration> imports = new ArrayList<>(cu.findAll(ImportDeclaration.class));
        return imports;
    }

    private static void findUnusedImports(CompilationUnit cu) {
        // Step 1: Collect all import declarations
        List<ImportDeclaration> imports = cu.findAll(ImportDeclaration.class);
        Set<String> importDeclarations = new HashSet<>();
        imports.forEach(importDecl -> importDeclarations.add(importDecl.getNameAsString()));

        // Step 2: Collect all referenced class names, methods, etc.
        Set<String> usedTypes = new HashSet<>();
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                super.visit(n, arg);
                usedTypes.add(n.resolve().getQualifiedName());
            }

            @Override
            public void visit(MethodDeclaration n, Void arg) {
                super.visit(n, arg);
                if (n.getType().isClassOrInterfaceType()) {
                    usedTypes.add(n.resolve().getQualifiedName());
                }
            }

            @Override
            public void visit(NameExpr n, Void arg) {
                super.visit(n, arg);
                try {
                    // Try to resolve the NameExpr
                    ResolvedDeclaration resolvedDeclaration = n.resolve();
                    System.out.println("Name: " + n.getName());
                    System.out.println("Resolved Declaration: " + resolvedDeclaration);
                } catch (Exception e) {
                    // Handle the case where the NameExpr cannot be resolved
                    System.out.println("Unable to resolve: " + n.getName());
                }
            }

            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                super.visit(n, arg);
                usedTypes.add(n.resolve().getQualifiedName());
            }

            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                usedTypes.add(n.resolve().getQualifiedName());
            }
        }, null);

        // Step 3: Find unused imports
        Set<String> unusedImports = new HashSet<>(importDeclarations);
        System.out.println("Used type: " + usedTypes);
        unusedImports.removeAll(usedTypes);

        // Step 4: Print unused imports
        if (unusedImports.isEmpty()) {
            System.out.println("No unused imports found.");
        } else {
            System.out.println("Unused imports:");
            unusedImports.forEach(System.out::println);
        }
    }

    private static ArrayList<String> listJavaFolders() {
        ArrayList<String> javaProjectFolders = new ArrayList<>();
        String rootDirectoryPath = "~/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software";
        File rootDirectory = new File(rootDirectoryPath.replace("~", System.getProperty("user.home")));

        if (rootDirectory.exists() && rootDirectory.isDirectory()) {
            File[] files = rootDirectory.listFiles();
            if (files != null) {
                Arrays.stream(files)
                        .filter(File::isDirectory)
                        .filter(Main::containsJavaFiles)
                        .forEach(folder -> javaProjectFolders.add(folder.getAbsolutePath()));
            }
        }
        return javaProjectFolders;
    }

    private static boolean containsJavaFiles(File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return false;
        }
        return Arrays.stream(files).anyMatch(file -> file.isFile() && file.getName().endsWith(".java")) ||
                Arrays.stream(files).filter(File::isDirectory).anyMatch(Main::containsJavaFiles);
    }

    private static void initParser(String jarPath) throws IOException {
        JarTypeSolver jarTypeSolver = new JarTypeSolver(jarPath);
        ReflectionTypeSolver reflectionTypeSolver = new ReflectionTypeSolver();
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        StaticJavaParser.setConfiguration(config);

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(reflectionTypeSolver);
        combinedTypeSolver.add(jarTypeSolver);
        combinedTypeSolver.add(new JarTypeSolver("/Users/nabhansuwanachote/.gradle/caches/modules-2/files-2.1/org.bytedeco/opencl/3.0-1.5.9/1216027a00c95d3a791fc67b8c8b183a2ed243e2/opencl-3.0-1.5.9-windows-x86_64.jar"));
        combinedTypeSolver.add(new JarTypeSolver("/Users/nabhansuwanachote/.gradle/caches/modules-2/files-2.1/org.bytedeco/opencl/3.0-1.5.9/8a2293bffedeab25915d59de438613c133c27d11/opencl-3.0-1.5.9-linux-x86_64.jar"));
        combinedTypeSolver.add(new JarTypeSolver("/Users/nabhansuwanachote/.gradle/caches/modules-2/files-2.1/org.bytedeco/opencl/3.0-1.5.9/b166ace7c905ab6b360fb048c95c8ce09109cc81/opencl-3.0-1.5.9.jar"));
        combinedTypeSolver.add(new JarTypeSolver("/Users/nabhansuwanachote/.gradle/caches/modules-2/files-2.1/org.bytedeco/opencl/3.0-1.5.9/c790fb16283708f5b37226fd26013f3b7b11f0f6/opencl-3.0-1.5.9-linux-arm64.jar"));
//        combinedTypeSolver.add(javaParserTypeSolver);
//        combinedTypeSolver.add(new JavaParserTypeSolver(new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-java-toolkit/src")));

//        for (String s : repoPath) {
//            combinedTypeSolver.add(new JavaParserTypeSolver(new File(s)));
//        }

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        StaticJavaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
    }

    private static void generateAstAndResolveType(CompilationUnit cu) throws IOException {
        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            try {
                var resolvedMethod = methodCall.resolve();
                System.out.println("Method Call: " + methodCall.getNameAsString());
                System.out.println("Qualified Class Name: " + resolvedMethod.getQualifiedName());

                methodCall.findAncestor(MethodDeclaration.class).ifPresent(parentMethod -> {
                    System.out.println("Parent Method: " + parentMethod.getNameAsString());
                });

                methodCall.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(parentClass -> {
                    System.out.println("Containing Class: " + parentClass.getNameAsString());
                });
            } catch (Exception e) {
                System.out.println("Unable to resolve method call: " + methodCall.getNameAsString());
            }
        });

        // Find and print field types
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            field.getVariables().forEach(variable -> {
                try {
                    ResolvedValueDeclaration resolvedValue = variable.resolve();
                    System.out.println("Field: " + variable.getNameAsString());
                    System.out.println("Field Type: " + resolvedValue.getType().describe());

                    field.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(parentClass -> {
                        System.out.println("Containing Class: " + parentClass.getNameAsString());
                    });
                } catch (Exception e) {
                    System.out.println("Unable to resolve field: " + variable.getNameAsString());
                }
            });
        });

        // Find and print parameter types
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            method.getParameters().forEach(parameter -> {
                try {
                    Parameter param = parameter;
                    var resolvedType = param.getType().resolve();
                    System.out.println("Parameter: " + param.getNameAsString());
                    System.out.println("Parameter Type: " + resolvedType.describe());

                    parameter.findAncestor(MethodDeclaration.class).ifPresent(parentMethod -> {
                        System.out.println("Parent Method: " + parentMethod.getNameAsString());
                    });

                    parameter.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(parentClass -> {
                        System.out.println("Containing Class: " + parentClass.getNameAsString());
                    });
                } catch (Exception e) {
                    System.out.println("Unable to resolve parameter: " + parameter.getNameAsString());
                }
            });
        });

        // Find and print variable declaration types
        cu.findAll(VariableDeclarationExpr.class).forEach(variableDeclaration -> {
            variableDeclaration.getVariables().forEach(variable -> {
                try {
                    ResolvedValueDeclaration resolvedValue = variable.resolve();
                    System.out.println("Variable: " + variable.getNameAsString());
                    System.out.println("Variable Type: " + resolvedValue.getType().describe());

                    variableDeclaration.findAncestor(MethodDeclaration.class).ifPresent(parentMethod -> {
                        System.out.println("Parent Method: " + parentMethod.getNameAsString());
                    });

                    variableDeclaration.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(parentClass -> {
                        System.out.println("Containing Class: " + parentClass.getNameAsString());
                    });
                } catch (Exception e) {
                    System.out.println("Unable to resolve variable: " + variable.getNameAsString());
                }
            });
        });

        // Find and print assignment expressions and their types
        cu.findAll(AssignExpr.class).forEach(assignExpr -> {
            try {
                Expression target = assignExpr.getTarget();
                ResolvedType resolvedType = target.calculateResolvedType();
                System.out.println("Assignment Target: " + target.toString());
                System.out.println("Assignment Target Type: " + resolvedType.describe());

                assignExpr.findAncestor(MethodDeclaration.class).ifPresent(parentMethod -> {
                    System.out.println("Parent Method: " + parentMethod.getNameAsString());
                });

                assignExpr.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(parentClass -> {
                    System.out.println("Containing Class: " + parentClass.getNameAsString());
                });
            } catch (Exception e) {
                System.out.println("Unable to resolve assignment target: " + assignExpr.toString());
            }
        });
    }
}

class Model {
    private final Node node;
    private final ResolvedType resolvedType;
    private final String importName;
    private final String typeName;

    public Model(Node node, ResolvedType resolvedType, String importName, String typeName) {
        this.typeName = typeName;
        this.node = node;
        this.resolvedType = resolvedType;
        this.importName = importName;

    }

    public Node getNode() {
        return node;
    }

    public ResolvedType getResolvedType() {
        return resolvedType;
    }

    public String getImportName() {
        return importName;
    }

    public String getTypeName() {
        return typeName;
    }
}
