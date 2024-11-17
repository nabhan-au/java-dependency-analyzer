package org.analyzer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.github.javaparser.javadoc.description.JavadocDescriptionElement;
import com.github.javaparser.javadoc.description.JavadocInlineTag;
import com.github.javaparser.javadoc.description.JavadocSnippet;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.Pair;
import org.analyzer.models.ImportClassPath;
import org.analyzer.models.ImportDetails;
import org.analyzer.models.SingleImportDetails;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.analyzer.FileUtils.getFileList;

public class DependencyResolver {
    private CompilationUnit compilationUnit;
    public ImportDetails importDetails;
    public List<ImportClassPath> unableImport;
    public List<ImportDeclaration> fileImports;
    public List<String> checkFullPathCalling = new ArrayList<>();

    public DependencyResolver(CompilationUnit compilationUnit, StaticImportInspectorFromJar staticImportInspectorFromJar) {
        this.compilationUnit = compilationUnit;
        var imports = this.compilationUnit.findAll(ImportDeclaration.class).toArray(ImportDeclaration[]::new);
        this.fileImports = Arrays.stream(imports).toList();
        List<ImportClassPath>  importList = Arrays.stream(imports).map(classImport -> new ImportClassPath(classImport.toString().trim())).toList();
        var result = staticImportInspectorFromJar.getImportDetailsWithMultipleClassPath(importList);
        importDetails = result.a;
        unableImport = result.b;
    }

    public List<String> getJarFileImportDetails() {
        return importDetails.classList.stream().map(c -> c.classPath.toString()).toList();
    }

    public Pair<List<SingleImportDetails>, List<String>> resolveNodeType(Node node) {
        List<SingleImportDetails> result = new ArrayList<>();
        List<String> unableResolveImport = new ArrayList<>();
        List<String> checkFullPathImport = new ArrayList<>();
        if (node instanceof FieldDeclaration fieldResolvedNode) {
            var variables = fieldResolvedNode.getVariables();
            processInnerType(variables, result, unableResolveImport);
            fieldResolvedNode.getJavadoc().ifPresent(doc -> processJavadoc(doc, result, unableResolveImport));
        } else if (node instanceof VariableDeclarator variableResolvedNode) {
            processType(variableResolvedNode.getType(), result, unableResolveImport);
            variableResolvedNode.getInitializer().ifPresent(t -> {
                var tempResult = resolveNodeType(t);
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
            });
            processType(variableResolvedNode.getType(), result, unableResolveImport);
        } else if (node instanceof ClassOrInterfaceDeclaration classResolvedNode) {
            var extendTypes = classResolvedNode.getExtendedTypes();
            var implementTypes = classResolvedNode.getImplementedTypes();
            var permittedTypes = classResolvedNode.getPermittedTypes();


            extendTypes.forEach(types -> processType(types, result, unableResolveImport));
            implementTypes.forEach(types -> processType(types, result, unableResolveImport));
            permittedTypes.forEach(types -> processType(types, result, unableResolveImport));

            var tempResult = extractTypeParams(classResolvedNode.getTypeParameters());
            result.addAll(tempResult.a);
            unableResolveImport.addAll(tempResult.b);
            classResolvedNode.getJavadoc().ifPresent(doc -> processJavadoc(doc, result, unableResolveImport));
            // TODO javadoc
        } else if (node instanceof VariableDeclarationExpr variableResolvedNode) {
            var variables = variableResolvedNode.getVariables();
            for (var variable : variables) {
                var tempResult = resolveNodeType(variable);
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
            }
        } else if (node instanceof InstanceOfExpr instanceOfExprResolvedNode) {
            var tempResult = resolveNodeType(instanceOfExprResolvedNode.getExpression());
            result.addAll(tempResult.a);
            unableResolveImport.addAll(tempResult.b);
            processType(instanceOfExprResolvedNode.getType(), result, unableResolveImport);
        }  else if (node instanceof CastExpr castExprResolvedNode) {
            var tempResult = resolveNodeType(castExprResolvedNode.getExpression());
            result.addAll(tempResult.a);
            unableResolveImport.addAll(tempResult.b);
            processType(castExprResolvedNode.getType(), result, unableResolveImport);
        } else if (node instanceof Parameter parameterResolvedNode) {
            processType(parameterResolvedNode.getType(), result, unableResolveImport);
        } else if (node instanceof MethodDeclaration methodResolvedNode) {
            processType(methodResolvedNode.getType(), result, unableResolveImport);
            var typeParamResult = extractTypeParams(methodResolvedNode.getTypeParameters());
            result.addAll(typeParamResult.a);
            unableResolveImport.addAll(typeParamResult.b);
            methodResolvedNode.getJavadoc().ifPresent(doc -> processJavadoc(doc, result, unableResolveImport));
            // TODO javadoc
        } else if (node instanceof MethodCallExpr methodCallResolvedNode) {
            var methodArguments = methodCallResolvedNode.getArguments();
            var methodTypeArguments = methodCallResolvedNode.getTypeArguments();
            var methodName = methodCallResolvedNode.getNameAsString();
            var methodScope = methodCallResolvedNode.getScope();

            if (methodScope.isPresent()) {
                processScope(methodScope.get(), result, unableResolveImport);
            } else {
                addImportMethodToResult(methodName, result, unableResolveImport);
            }

            if (methodTypeArguments.isPresent()) {
                var types = methodTypeArguments.get().stream().flatMap(t -> {
                    checkFullPathImport.add(t.toString());
                    return MyTypeSolvers.splitStructuredTypes(t.toString()).stream();
                }).toList();
                types.forEach(type -> addImportClassToResult(type, result, unableResolveImport));
            }

            methodArguments.forEach(m -> {
                var tempResult = resolveNodeType(m);
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
            });
        } else if (node instanceof NameExpr nameExprResolvedNode) {
            var name = nameExprResolvedNode.getName();
            addImportFieldToResult(name.toString(), result, unableResolveImport);
        } else if (node instanceof EnumDeclaration enumResolvedNode) {
            var implementType = enumResolvedNode.getImplementedTypes();
            var extractedImplementType = implementType.stream().flatMap(t -> {
                checkFullPathImport.add(t.toString());
                return MyTypeSolvers.splitStructuredTypes(t.toString()).stream();
            }).toList();
            extractedImplementType.forEach(type -> {
                addImportClassToResult(type, result, unableResolveImport);
            });
            var enumConstantDeclaration = enumResolvedNode.getEntries();
            enumConstantDeclaration.forEach(c -> {
                var tempResult = resolveNodeType(c);
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
            });
        } else if (node instanceof EnumConstantDeclaration enumConstantDeclarationResolvedNode) {
            enumConstantDeclarationResolvedNode.getArguments().forEach(c -> {
                var tempResult = resolveNodeType(c);
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
            });
        } else if (node instanceof AssignExpr assignExprResolvedNode) {
            var tempValueResult = resolveNodeType(assignExprResolvedNode.getValue());
            var tempTargetResult = resolveNodeType(assignExprResolvedNode.getTarget());
            result.addAll(tempValueResult.a);
            unableResolveImport.addAll(tempValueResult.b);
            result.addAll(tempTargetResult.a);
            unableResolveImport.addAll(tempTargetResult.b);
        } else if (node instanceof AnnotationExpr annotationExprResolvedNode) {
            checkFullPathImport.add(annotationExprResolvedNode.getNameAsString());
            var annotationType = MyTypeSolvers.splitStructuredTypes(annotationExprResolvedNode.getNameAsString());
            annotationType.forEach(a -> addImportClassToResult(a, result, unableResolveImport));
            if (annotationExprResolvedNode.isSingleMemberAnnotationExpr()) {
                var singleMemberAnnotation = annotationExprResolvedNode.asSingleMemberAnnotationExpr();
                var tempResult = resolveNodeType(singleMemberAnnotation.getMemberValue());
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
                try {
                    importDetails.getClass(annotationExprResolvedNode.getNameAsString());
                    result.addAll(tempResult.a);
                } catch (ClassNotFoundException e) {
                    unableResolveImport.addAll(tempResult.b);
                }

            } else if (annotationExprResolvedNode.isNormalAnnotationExpr()) {
                var normalAnnotation = annotationExprResolvedNode.asNormalAnnotationExpr();
                normalAnnotation.getPairs().forEach(pair -> {
                    var tempResult = resolveNodeType(pair.getValue());
                    result.addAll(tempResult.a);
                    unableResolveImport.addAll(tempResult.b);
                });
            }
        } else if (node instanceof ArrayInitializerExpr arrayInitializerExprResolvedNode) {
            arrayInitializerExprResolvedNode.getValues().forEach(e -> {
                var tempResult = resolveNodeType(e);
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
            });
        } else if (node instanceof ExplicitConstructorInvocationStmt explicitConstructorInvocationStmtResolvedNode) {
            explicitConstructorInvocationStmtResolvedNode.getExpression().ifPresent(t -> {
                var tempResult = resolveNodeType(t);
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
            });
            explicitConstructorInvocationStmtResolvedNode.getArguments().forEach(c -> {
                var tempResult = resolveNodeType(c);
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
            });
            explicitConstructorInvocationStmtResolvedNode.getTypeArguments().ifPresent(t -> t.forEach(c -> {
                var tempResult = resolveNodeType(c);
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
            }));
        } else if (node instanceof AnnotationMemberDeclaration annotationMemberDeclarationResolvedNode) {
            processType(annotationMemberDeclarationResolvedNode.getType(), result, unableResolveImport);
        } else if (node instanceof FieldAccessExpr fieldAccessExprResolvedNode) {
            processScope(fieldAccessExprResolvedNode.getScope(), result, unableResolveImport);
            var typeArguments = fieldAccessExprResolvedNode.getTypeArguments();
            typeArguments.ifPresent(types -> types.forEach(t -> processType(t, result, unableResolveImport)));
        } else if (node instanceof ObjectCreationExpr objectCreationExprResolvedNode) {;
            processType(objectCreationExprResolvedNode.getType(), result, unableResolveImport);
            processInnerType(objectCreationExprResolvedNode.getArguments(), result, unableResolveImport);
            var typeArguments = objectCreationExprResolvedNode.getTypeArguments();
            typeArguments.ifPresent(types -> types.forEach(t -> processType(t, result, unableResolveImport)));
            var scope = objectCreationExprResolvedNode.getScope();
            scope.ifPresent(s -> processScope(s, result, unableResolveImport));
        } else if (node instanceof MethodReferenceExpr methodReferenceExprResolvedNode) {
            var typeArguments = methodReferenceExprResolvedNode.getTypeArguments();
            typeArguments.ifPresent(types -> types.forEach(t -> processType(t, result, unableResolveImport)));
            processScope(methodReferenceExprResolvedNode.getScope(), result, unableResolveImport);
        } else if (node instanceof TypePatternExpr typePatternExprResolvedNode) {
            processType(typePatternExprResolvedNode.getType(), result, unableResolveImport);
        } else if (node instanceof BinaryExpr binaryExprResolvedNode) {
            processInnerType(new NodeList<>(binaryExprResolvedNode.getLeft(), binaryExprResolvedNode.getRight()), result, unableResolveImport);
        } else if (node instanceof UnaryExpr unaryExprResolvedNode) {
            processInnerType(new NodeList<>(unaryExprResolvedNode.getExpression()), result, unableResolveImport);
        } else if (node instanceof TypeExpr typeExpr) {
            processInnerType(new NodeList<>(typeExpr.getType()), result, unableResolveImport);
        } else if (node instanceof SwitchExpr switchExprResolvedNode) {
            switchExprResolvedNode.getEntries().forEach(s -> {
                processInnerType(s.getLabels(), result, unableResolveImport);
                s.getGuard().ifPresent(g -> processInnerType(new NodeList<>(g), result, unableResolveImport));
            });
        } else if (node instanceof PatternExpr patternExprResolvedNode) {
            processType(patternExprResolvedNode.getType(), result, unableResolveImport);
        } else if (node instanceof ForStmt forStmtResolvedNode) {
            processInnerType(forStmtResolvedNode.getInitialization(), result, unableResolveImport);
            processInnerType(forStmtResolvedNode.getUpdate(), result, unableResolveImport);
            if (forStmtResolvedNode.getCompare().isPresent()) {
                processInnerType(new NodeList<>(forStmtResolvedNode.getCompare().get()), result, unableResolveImport);
            }
        } else if (node instanceof ForEachStmt forEachStmtResolvedNode) {
            processInnerType(new NodeList<>(forEachStmtResolvedNode.getVariable()), result, unableResolveImport);
            processInnerType(new NodeList<>(forEachStmtResolvedNode.getIterable()), result, unableResolveImport);
            processInnerType(new NodeList<>(forEachStmtResolvedNode.getVariableDeclarator()), result, unableResolveImport);
        }
        else if (node instanceof ClassExpr classExprResolvedNode) {
            processType(classExprResolvedNode.getType(), result, unableResolveImport);
        }
        else if (node instanceof CatchClause catchClauseResolvedNode) {
            processInnerType(new NodeList<>(catchClauseResolvedNode.getParameter()), result, unableResolveImport);
        }
        else if (node instanceof ReferenceType referenceTypeResolvedNode) {
            processTypeStr(referenceTypeResolvedNode.toString(), result, unableResolveImport);
        } else if (node instanceof Type type) {
            processType(type, result, unableResolveImport);
        }
        return new Pair<>(result, unableResolveImport);
    }

    private void processTypeStr(String typeStr, List<SingleImportDetails> result, List<String> unableResolveImport) {
        checkFullPathCalling.add(typeStr);
        var types = MyTypeSolvers.splitStructuredTypes(typeStr);
        types.forEach(type -> {
            addImportClassToResult(type, result, unableResolveImport);
        });
    }

    private <T extends Node> void processInnerType(NodeList<T> expressions, List<SingleImportDetails> result, List<String> unableResolveImport) {
        expressions.forEach(e -> {
            var tempResult = resolveNodeType(e);
            result.addAll(tempResult.a);
            unableResolveImport.addAll(tempResult.b);
        });
    }

    private void processScope(Expression scope, List<SingleImportDetails> result, List<String> unableResolveImport) {
        // Check static object call
        checkFullPathCalling.add(scope.toString());
        MyTypeSolvers.splitStructuredTypes(scope.toString()).forEach(t -> addImportClassToResult(t, result, unableResolveImport));

        // Check inner call
        var tempResult = resolveNodeType(scope);
        result.addAll(tempResult.a);
        unableResolveImport.addAll(tempResult.b);
    }

    private void processType(Type variableResolvedNode, List<SingleImportDetails> result, List<String> unableResolveImport) {
        checkFullPathCalling.add(variableResolvedNode.toString());
        var types = MyTypeSolvers.splitStructuredTypes(variableResolvedNode.asString());
        types.forEach(type -> {
            addImportClassToResult(type, result, unableResolveImport);
        });
    }

    private void addImportFieldToResult(String field, List<SingleImportDetails> result, List<String> unableResolveImport) {
        try {
            result.add(importDetails.getField(field));
        } catch (Exception e) {
            unableResolveImport.add(field);
        }
    }

    private void addImportMethodToResult(String method, List<SingleImportDetails> result, List<String> unableResolveImport) {
        try {
            result.add(importDetails.getMethod(method));
        } catch (Exception e) {
            unableResolveImport.add(method);
        }
    }

    private void addImportClassToResult(String type, List<SingleImportDetails> result, List<String> unableResolveImport) {
        try {
            result.add(importDetails.getClass(type));
        } catch (Exception e) {
            unableResolveImport.add(type);
        }
    }

    public Pair<List<SingleImportDetails>, List<String>> extractTypeParams(NodeList<TypeParameter> typeParameters) {
        List<SingleImportDetails> result = new ArrayList<>();
        List<String> unableResolveImport = new ArrayList<>();
        typeParameters.forEach(typeParameter -> {
            var types = typeParameter.getTypeBound().stream().flatMap(t -> MyTypeSolvers.splitStructuredTypes(t.toString()).stream()).toList();
            types.forEach(type -> addImportClassToResult(type, result, unableResolveImport));
        });
        return new Pair<>(result, unableResolveImport);
    }

    public List<ImportClassPath> getUnableImport() {
        return unableImport;
    }

    public void processJavadoc(Javadoc javadoc, List<SingleImportDetails> result, List<String> unableResolveImport) {
        List<JavadocBlockTag> blockTags = javadoc.getBlockTags();
        blockTags.forEach(blockTag -> {
            switch (blockTag.getType()) {
                case SEE -> {
                    var elements = blockTag.getContent().getElements();
                    JavadocSnippet snippet = elements.stream().filter(e -> e instanceof JavadocSnippet).map(e -> (JavadocSnippet) e).findFirst().orElse(null);
                    if (snippet != null) {
                        var functionCall = snippet.toText().split(" ")[0].trim();
                        var className = functionCall.split("#")[0];
                        processTypeStr(className, result, unableResolveImport);
                    }
                    processJavadocDescriptionElement(elements, result, unableResolveImport);
                }
                case EXCEPTION, THROWS -> {
                    var className = blockTag.getName().orElse("");
                    processTypeStr(className, result, unableResolveImport);
                    var elements = blockTag.getContent().getElements();
                    processJavadocDescriptionElement(elements, result, unableResolveImport);

                }
            }
        });
        var javaDocElement = javadoc.getDescription().getElements();
        processJavadocDescriptionElement(javaDocElement, result, unableResolveImport);
    }

    public void processJavadocDescriptionElement(List<JavadocDescriptionElement> javadocDescriptionElements, List<SingleImportDetails> result, List<String> unableResolveImport) {
        String regex = "\\(([^)]+)\\)";
        Pattern pattern = Pattern.compile(regex);

        javadocDescriptionElements.forEach(e -> {
            if (e instanceof JavadocInlineTag javaDocInlineTag) {
                if (javaDocInlineTag.getType() ==  JavadocInlineTag.Type.LINK) {
                    if (javaDocInlineTag.getContent().contains("#")) {
                        var splitContent = javaDocInlineTag.getContent().split("#", 2);
                        var className = splitContent[0].trim();
                        var member = splitContent[1].trim();
                        processTypeStr(className, result, unableResolveImport);
                        if (member.contains("(") && member.contains(")")) {
                            Matcher matcher = pattern.matcher(splitContent[1]);
                            if (matcher.find()) {
                                String parameters = matcher.group(1);
                                Arrays.stream(parameters.split(",")).forEach(f -> {
                                    processTypeStr(f, result, unableResolveImport);
                                });
                            }
                        }
                    } else {
                        var className = javaDocInlineTag.getContent().trim();
                        processTypeStr(className, result, unableResolveImport);
                    }
                }
            }
        });
    }

    public static void main(String[] args) throws Exception {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        ParserConfiguration parserConfig = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        StaticJavaParser.setConfiguration(parserConfig);

        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception";
        var destinationPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/temp-repo";
        var files = getFileList(repoPath + "/src/main/java");
//        var directDependencies = GradleDependenciesExtractor.getProjectDependencies(repoPath);
//        var installer = new ArtifactInstaller();
//        var currentRepoArtifact = "us.ihmc:ihmc-perception:0.14.0-241016";
//        var extractedCurrentDependency = GradleDependenciesExtractor.extractDependency(currentRepoArtifact);
//        var installResult = installer.install(extractedCurrentDependency, destinationPath, false);
//        var currentRepoArtifactPath = installResult.a;
//        List<File> artifactPaths = new ArrayList<>(Arrays.asList(new File(currentRepoArtifactPath.getArtifactDirectory())));
//        directDependencies.forEach(d -> {
//            var extractedDependency = GradleDependenciesExtractor.extractDependency(d.toString());
//            try {
//                var artifactPath = installer.install(extractedDependency.get(0), extractedDependency.get(1), extractedDependency.get(2), destinationPath);
//                artifactPaths.add(new File(artifactPath));
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        });
        for (Path path : files) {
            var jarFile = new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar");
//            var jarFile = new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/temp-repo/ihmc-perception.main.jar");
            CompilationUnit cu = StaticJavaParser.parse(new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java/us/ihmc/perception/comms/PerceptionComms.java"));
//            CompilationUnit cu = StaticJavaParser.parse(new File(path.toAbsolutePath().toString()));
            List<File> artifactPaths = new ArrayList<>();
            artifactPaths.add(jarFile);
            var staticImportInspectorFromJar = new StaticImportInspectorFromJar(artifactPaths);
            // jar:file:/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar!/
            var resolver = new DependencyResolver(cu, staticImportInspectorFromJar);
//            resolver.importDetails.classList.forEach(c -> System.out.println(c.importObject));
            var importList = new ArrayList<SingleImportDetails>();
            var unable = new ArrayList<String>();
            cu.walk(node -> {
//            if (node instanceof ReferenceType) {
//                System.out.println(node);
                var result = resolver.resolveNodeType(node);
                importList.addAll(result.a);
                unable.addAll(result.b);
//                System.out.println(result.a.stream().map(t -> t.importObject).toList());
//                System.out.println(result.b);
//            }
            });
            cu.findAll(JavadocComment.class).forEach(n -> {
//                System.out.println(n.getContent());
            });

            // TODO use checkFullPathCalling to check with dependency path directly
            var todo1 = resolver.checkFullPathCalling;
            var checkedImportList = importList.stream().map(t -> t.classPath.getOriginalPath().trim()).distinct().toList();
            var fileImport = resolver.fileImports;

            var unusedImport = new ArrayList<ImportDeclaration>();
            var usedImport = new ArrayList<ImportDeclaration>();
            for (ImportDeclaration importDeclaration : fileImport) {
                System.out.println(importDeclaration.toString().trim());
                System.out.println(checkedImportList);

                if (!checkedImportList.contains(importDeclaration.toString().trim())) {
                    unusedImport.add(importDeclaration);
                }
            }
            for (ImportDeclaration importDeclaration : fileImport) {
                if (checkedImportList.contains(importDeclaration.toString().trim())) {
                    usedImport.add(importDeclaration);
                }
            }
            if (!unusedImport.isEmpty()) {
                System.out.println(path.toUri());
                for (ImportDeclaration importDeclaration : unusedImport) {
                    System.out.println("Unused import: " + importDeclaration);
                }
//                System.out.println(importList.stream().map(c -> c.importObject.toString()).toList());
//                System.out.println(unable.stream().distinct().toList());
                System.out.println("--------------------------");
            }
//            System.out.println(resolver.importDetails.classList.stream().map(t -> t.importObject).toList());
            break;
        }
    }
}
