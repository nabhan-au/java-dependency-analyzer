package org.analyzer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.Pair;
import org.analyzer.models.ImportClassPath;
import org.analyzer.models.ImportDetails;
import org.analyzer.models.SingleImportDetails;
import org.checkerframework.checker.units.qual.N;

import java.awt.*;
import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.analyzer.Main.getFileList;

public class DependencyResolver {
    private CompilationUnit compilationUnit;
    public ImportDetails importDetails;
    public List<ImportClassPath> unableImport;
    public List<ImportDeclaration> fileImports;

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
        if (node instanceof FieldDeclaration fieldResolvedNode) {
            var variables = fieldResolvedNode.getVariables();
            processInnerType(variables, result, unableResolveImport);
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
            var extractedExtendTypes = extendTypes.stream().flatMap(types -> MyTypeSolvers.splitStructuredTypes(types.toString()).stream()).toList();
            var extractedImplementTypes = implementTypes.stream().flatMap(types -> MyTypeSolvers.splitStructuredTypes(types.toString()).stream()).toList();
            var extractedPermittedTypes = permittedTypes.stream().flatMap(types -> MyTypeSolvers.splitStructuredTypes(types.toString()).stream()).toList();
            extractedExtendTypes.forEach(type -> {
                addImportClassToResult(type, result, unableResolveImport);
            });
            extractedImplementTypes.forEach(type -> {
                addImportClassToResult(type, result, unableResolveImport);
            });
            extractedPermittedTypes.forEach(type -> {
                addImportClassToResult(type, result, unableResolveImport);
            });
            var tempResult = extractTypeParams(classResolvedNode.getTypeParameters());
            result.addAll(tempResult.a);
            unableResolveImport.addAll(tempResult.b);
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
                var types = methodTypeArguments.get().stream().flatMap(t -> MyTypeSolvers.splitStructuredTypes(t.toString()).stream()).toList();
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
            var extractedImplementType = implementType.stream().flatMap(t -> MyTypeSolvers.splitStructuredTypes(t.toString()).stream()).toList();
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
            var types = MyTypeSolvers.splitStructuredTypes(referenceTypeResolvedNode.asString());
            types.forEach(type -> {
                addImportClassToResult(type, result, unableResolveImport);
            });
        } else if (node instanceof Type type) {
            processType(type, result, unableResolveImport);
        }
        return new Pair<>(result, unableResolveImport);
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
        MyTypeSolvers.splitStructuredTypes(scope.toString()).forEach(t -> addImportClassToResult(t, result, unableResolveImport));

        // Check inner call
        var tempResult = resolveNodeType(scope);
        result.addAll(tempResult.a);
        unableResolveImport.addAll(tempResult.b);
    }

    private void processType(Type variableResolvedNode, List<SingleImportDetails> result, List<String> unableResolveImport) {
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

    public static void main(String[] args) throws Exception {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        ParserConfiguration parserConfig = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        StaticJavaParser.setConfiguration(parserConfig);

        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception";
        var files = getFileList(repoPath + "/src/main/java");

        for (Path path : files) {
            var jarFile = new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar");
            CompilationUnit cu = StaticJavaParser.parse(new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java/us/ihmc/sensors/ZEDColorDepthImagePublisher.java"));
//            CompilationUnit cu = StaticJavaParser.parse(new File(path.toAbsolutePath().toString()));
            var staticImportInspectorFromJar = new StaticImportInspectorFromJar(jarFile);
            var resolver = new DependencyResolver(cu, staticImportInspectorFromJar);
            var importList = new ArrayList<SingleImportDetails>();
            var unable = new ArrayList<String>();
            cu.walk(node -> {
//            if (node instanceof ClassExpr) {
//                System.out.println(node);
                var result = resolver.resolveNodeType(node);
                importList.addAll(result.a);
                unable.addAll(result.b);
//                System.out.println(result.a.stream().map(t -> t.importObject).toList());
//                System.out.println(result.b);
//            }
            });
            var checkedImportList = importList.stream().map(t -> t.classPath.getOriginalPath()).distinct().toList();
            var fileImport = resolver.fileImports;

            var unusedImport = new ArrayList<ImportDeclaration>();
            for (ImportDeclaration importDeclaration : fileImport) {
                if (!checkedImportList.contains(importDeclaration.toString().trim())) {
                    unusedImport.add(importDeclaration);
                }
            }
            if (!unusedImport.isEmpty()) {
                System.out.println(path.toUri());
                for (ImportDeclaration importDeclaration : unusedImport) {
                    System.out.println("Unused import: " + importDeclaration);
                }
                System.out.println(importList.stream().map(c -> c.importObject.toString()).toList());
                System.out.println(unable.stream().distinct().toList());
                System.out.println("--------------------------");
            }
            System.out.println(resolver.importDetails.classList.stream().map(t -> t.importObject).toList());
            break;
        }

//        for (FieldDeclaration f : field) {
//            var results = f.getVariables().stream().flatMap(fs -> MyTypeSolvers.extractCompoundType(fs.getType().asString()).stream());
//            System.out.println(f.getVariables());
//            System.out.println(f);
//            results.forEach(System.out::println);
//            System.out.println("================================================================");
//        }
//        var variable = cu.findAll(VariableDeclarator.class);
//        for (VariableDeclarator m : variable) {
//            var results = MyTypeSolvers.extractCompoundType(m.getType().asString());
//            System.out.println(m);
//            results.forEach(System.out::println);
//            System.out.println("================================================================");
//        }
//        var classDeclaration = cu.findAll(ClassOrInterfaceDeclaration.class);
//        for (ClassOrInterfaceDeclaration c : classDeclaration) {
//            System.out.println(c.getName());
//            System.out.println(c.getExtendedTypes());
//        }
//        var variableDeclarationExpr = cu.findAll(VariableDeclarationExpr.class);
//        for (VariableDeclarationExpr v : variableDeclarationExpr) {
//            var results = v.getVariables().stream().flatMap(vs -> MyTypeSolvers.extractCompoundType(vs.getType().asString()).stream());
//            System.out.println(v.getVariables());
//            results.forEach(System.out::println);
//            System.out.println("================================================================");
//        }
//        var instanceOfExpr = cu.findAll(InstanceOfExpr.class);
//        for (InstanceOfExpr i : instanceOfExpr) {
//            var results = MyTypeSolvers.extractCompoundType(i.getType().asString());
//        }
//
//        var castExpr = cu.findAll(CastExpr.class);
//        for (CastExpr c : castExpr) {
//            var results = MyTypeSolvers.extractCompoundType(c.getType().asString());
//        }
//        var params = cu.findAll(Parameter.class);
//        for (Parameter p : params) {
//            System.out.println(p.toString());
//            System.out.println(p.getType());
//            System.out.println("================================================================");
//        }
//
//        var name = cu.findAll(NameExpr.class);
//        for (NameExpr a : name) {
//            System.out.println(a);
//        }

//        var methodDecl = cu.findAll(MethodDeclaration.class);
//        for (MethodDeclaration m : methodDecl) {
//            System.out.println(m.toString());
//            System.out.println(m.getType());
//            System.out.println("================================================================");
//        }j
//
//        var methodCall = cu.findAll(MethodCallExpr.class);
//        for (MethodCallExpr m : methodCall) {
//            System.out.println(m.toString());
//            System.out.println(m.getScope());
//            System.out.println(m.getTypeArguments());
//            System.out.println(m.getArguments());
//            System.out.println("================================================================");
//        }
//        var enumDeclaration = cu.findAll(EnumDeclaration.class);
//        for (EnumDeclaration e : enumDeclaration) {
//            System.out.println(e.getEntries().stream().flatMap(entry -> entry.getArguments().stream()).toList());
//        }
//        var enumConstantDeclaration = cu.findAll(EnumConstantDeclaration.class);
//        for (EnumConstantDeclaration e : enumConstantDeclaration) {
//            System.out.println(e.getArguments());
//        }
//        var annotation = cu.findAll(AnnotationExpr.class);
//        for (AnnotationExpr a : annotation) {s
//            System.out.println(a.toString());
//            System.out.println(a.getName());
//            if (a.isSingleMemberAnnotationExpr()) {
//                var sa = a.asSingleMemberAnnotationExpr();
//                System.out.println(sa.getMemberValue());
//            }
//            if (a.isNormalAnnotationExpr()) {
//                var na = a.asNormalAnnotationExpr();
//                System.out.println(na.getPairs());
//            }
//        }
//        var explicitConsInvo = cu.findAll(ExplicitConstructorInvocationStmt.class);
//        for (ExplicitConstructorInvocationStmt e : explicitConsInvo) {
//            var explicitConsInvoArgs = e.getArguments();
//            explicitConsInvoArgs.forEach(System.out::println);
//            if (e.getTypeArguments().isPresent()) {
//                e.getTypeArguments().get().forEach(System.out::println);
//            }
//            System.out.println(e.getExpression());
//            System.out.println("================================================================");
//        }
//        var annotationDecl = cu.findAll(AnnotationDeclaration.class);
//        for (AnnotationDeclaration a : annotationDecl) {
//            System.out.println(a.getName());
//            System.out.println(a.getFields());
//        }
//        var annotationMemberDecl = cu.findAll(AnnotationMemberDeclaration.class);
//        for (AnnotationMemberDeclaration annotationDeclaration : annotationMemberDecl) {
//            System.out.println(annotationDeclaration.getAnnotations());
//            System.out.println(annotationDeclaration.getType());
//        }






//
//        var fieldAccess = cu.findAll(FieldAccessExpr.class);
//        for (FieldAccessExpr f : fieldAccess) {
//            System.out.println(f.toString());
//            System.out.println(f.getScope());
//            System.out.println(f.getTypeArguments());
//            System.out.println("================================================================");
//        }
//
//        var methodReference = cu.findAll(MethodReferenceExpr.class);
//        for (MethodReferenceExpr methodReferenceExpr : methodReference) {
//            System.out.println(methodReferenceExpr.toString());
//            System.out.println(methodReferenceExpr.getTypeArguments());
//            System.out.println(methodReferenceExpr.getScope());
//            // System.out.println(methodReferenceExpr.getIdentifier());
//            System.out.println("================================================================");
//        }
//
//        var objectCreation = cu.findAll(ObjectCreationExpr.class);
//        for (ObjectCreationExpr objectCreationExpr : objectCreation) {
//            System.out.println(objectCreationExpr.toString());
//            System.out.println(objectCreationExpr.getScope());
//            System.out.println(objectCreationExpr.getArguments());
//            System.out.println(objectCreationExpr.getTypeArguments());
//            System.out.println(objectCreationExpr.getType());
//            System.out.println("================================================================");
//        }
//

//        var typePatternExpr = cu.findAll(TypePatternExpr.class);
//        for (TypePatternExpr typePattern : typePatternExpr) {
//            System.out.println(typePattern.getName());
//            System.out.println(typePattern.getType());
//        }


//
//            var resolver = new DependencyResolver(cu, jarFile);
//            var method =  cu.findAll(MethodDeclaration.class).get(0);
//            var params =  cu.findAll(Parameter.class).get(0);
//            resolver.resolveNodeType(method);
//            resolver.resolveNodeType(params);
//        }

//        cu.findAll(VariableDeclarator.class).forEach(name -> {
//            System.out.println(name);
//            System.out.println(name.getType());
//        });

//        System.out.println(" ");
//        System.out.println(" ");
//        System.out.println(" ");
//        System.out.println("=====================================================================================================================");
//        for (ImportClassPath classPath : unableImport) {
//            System.out.println(classPath.getPath());
//        }
//        CompilationUnit cu = StaticJavaParser.parse(new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java/us/ihmc/sensors/ZEDColorDepthImagePublisher.java"));
//        var importList = cu.findAll(ImportDeclaration.class).toArray(ImportDeclaration[]::new);
//        new DependencyResolver(new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar"), importList);

    }
}
