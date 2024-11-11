package org.analyzer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.Pair;
import org.analyzer.models.ImportClassPath;
import org.analyzer.models.ImportDetails;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.analyzer.Main.getFileList;

public class DependencyResolver {
    private CompilationUnit compilationUnit;
    private ImportDetails importDetails;
    private List<ImportClassPath> unableImport;

    public DependencyResolver(CompilationUnit compilationUnit, StaticImportInspectorFromJar staticImportInspectorFromJar) throws MalformedURLException {
        this.compilationUnit = compilationUnit;
        var imports = this.compilationUnit.findAll(ImportDeclaration.class).toArray(ImportDeclaration[]::new);
        List<ImportClassPath>  importList = Arrays.stream(imports).map(classImport -> new ImportClassPath(classImport.toString().trim())).toList();
        var result = staticImportInspectorFromJar.getImportDetailsWithMultipleClassPath(importList);
        importDetails = result.a;
        unableImport = result.b;

//        for (SingleImportDetails<Method> method : importDetails.methodList) {
//            System.out.println(method.importObject);
//        }
//        for (SingleImportDetails<Field> field: importDetails.fieldList) {
//            System.out.println(field.importObject);
//        }
//        for (SingleImportDetails<Class<?>> clazz: importDetails.classList) {
//            System.out.println(clazz.importObject);
//        }
    }

    public Pair<List<ImportClassPath>, List<String>> resolveNodeType(Node node) {
        List<ImportClassPath> result = new ArrayList<>();
        List<String> unableResolveImport = new ArrayList<>();
        if (node instanceof FieldDeclaration fieldResolvedNode) {
            var variables = fieldResolvedNode.getVariables();
            for (var variable : variables) {
                result.addAll(resolveNodeType(variable).a);
                unableResolveImport.addAll(resolveNodeType(variable).b);
            }
        } else if (node instanceof VariableDeclarator variableResolvedNode) {
            var types = MyTypeSolvers.extractCompoundType(variableResolvedNode.getType().asString());
            types.forEach(type -> {
                try {
                    result.add(importDetails.getClass(type));
                } catch (Exception e) {
                    unableResolveImport.add(type);
                }
            });
        } else if (node instanceof ClassOrInterfaceDeclaration classResolvedNode) {
            var extendTypes = classResolvedNode.getExtendedTypes();
            var extractedExtendTypes = extendTypes.stream().flatMap(types -> MyTypeSolvers.extractCompoundType(types.toString()).stream()).toList();
            extractedExtendTypes.forEach(type -> {
                try {
                    result.add(importDetails.getClass(type));
                } catch (Exception e) {
                    unableResolveImport.add(type);
                }
            });
        } else if (node instanceof VariableDeclarationExpr variableResolvedNode) {
            var variables = variableResolvedNode.getVariables();
            for (var variable : variables) {
                result.addAll(resolveNodeType(variable).a);
                unableResolveImport.addAll(resolveNodeType(variable).b);
            }
        } else if (node instanceof InstanceOfExpr instanceOfExprResolvedNode) {
            var types = MyTypeSolvers.extractCompoundType(instanceOfExprResolvedNode.getType().asString());
            types.forEach(type -> {
                try {
                    result.add(importDetails.getClass(type));
                } catch (Exception e) {
                    unableResolveImport.add(type);
                }
            });
        }  else if (node instanceof CastExpr castExprResolvedNode) {
            var types = MyTypeSolvers.extractCompoundType(castExprResolvedNode.getType().asString());
            types.forEach(type -> {
                try {
                    result.add(importDetails.getClass(type));
                } catch (Exception e) {
                    unableResolveImport.add(type);
                }
            });
        } else if (node instanceof Parameter parameterResolvedNode) {
            var types = MyTypeSolvers.extractCompoundType(parameterResolvedNode.getType().asString());
            types.forEach(type -> {
                try {
                    result.add(importDetails.getClass(type));
                } catch (Exception e) {
                    unableResolveImport.add(type);
                }
            });
        } else if (node instanceof MethodDeclaration methodResolvedNode) {
            var types = MyTypeSolvers.extractCompoundType(methodResolvedNode.getType().asString());
            types.forEach(type -> {
                try {
                    result.add(importDetails.getClass(type));
                } catch (Exception e) {
                    unableResolveImport.add(type);
                }
            });
        }

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
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        StaticJavaParser.setConfiguration(parserConfig);

//        var repoPath = "/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception";
//        var files = getFileList(repoPath + "/src/main/java");
        var jarFile = new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/test/artifacts/ihmc_perception_main_jar/ihmc-perception.main.jar");
//        List<ImportClassPath> unableImport = new ArrayList<>();
        CompilationUnit cu = StaticJavaParser.parse(new File("/Users/nabhansuwanachote/Desktop/research/msr-2025-challenge/repo/ihmc-open-robotics-software/ihmc-perception/src/main/java/us/ihmc/perception/demo/NVCompDemo.java"));
        var staticImportInspectorFromJar = new StaticImportInspectorFromJar(jarFile);
        var resolver = new DependencyResolver(cu, staticImportInspectorFromJar);



//        ArrayInitializerExpr
//        ConstructorCallExpr
//        BinaryExpr
//        UnaryExpr
//        InstanceOfExpr
//        TypeExpr

        var field = cu.findAll(Parameter.class);
        var results = field.stream().flatMap(f -> resolver.resolveNodeType(f).a.stream()).toList();
        var unableImports = field.stream().flatMap(f -> resolver.resolveNodeType(f).b.stream()).toList();
//        resolver.importDetails.classList.forEach(c -> System.out.println(c.importObject.getName()));
        System.out.println(results);
        System.out.println("=======================================================================");
        System.out.println(unableImports);
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
//        var assign = cu.findAll(NameExpr.class);
//        for (NameExpr a : assign) {
//            System.out.println(a);
//        }

//        var methodDecl = cu.findAll(MethodDeclaration.class);
//        for (MethodDeclaration m : methodDecl) {
//            System.out.println(m.toString());
//            System.out.println(m.getType());
//            System.out.println("================================================================");
//        }

//
//
//
//        var enumDeclaration = cu.findAll(EnumDeclaration.class);
//        for (EnumDeclaration e : enumDeclaration) {
//            System.out.println(e.getEntries().stream().flatMap(entry -> entry.getArguments().stream()).toList());
//        }
//
//        var enumConstantDeclaration = cu.findAll(EnumConstantDeclaration.class);
//        for (EnumConstantDeclaration e : enumConstantDeclaration) {
//            System.out.println(e.getArguments());
//        }
//
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
//
//
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
//
//        var methodCall = cu.findAll(MethodCallExpr.class);
//        for (MethodCallExpr m : methodCall) {
//            System.out.println(m.toString());
//            System.out.println(m.getScope());
//            System.out.println(m.getTypeArguments());
//            System.out.println(m.getArguments());
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
