package org.analyzer;

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
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.github.javaparser.javadoc.description.JavadocDescriptionElement;
import com.github.javaparser.javadoc.description.JavadocInlineTag;
import com.github.javaparser.javadoc.description.JavadocSnippet;
import com.github.javaparser.utils.Pair;
import org.analyzer.models.ImportClassPath;
import org.analyzer.models.ImportDetails;
import org.analyzer.models.SingleImportDetails;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DependencyResolver {
    private CompilationUnit compilationUnit;
    public ImportDetails importDetails;
    public List<ImportClassPath> failImports;
    public List<ImportDeclaration> fileImports;
    public List<String> checkFullPathCalling = new ArrayList<>();

    public DependencyResolver(CompilationUnit compilationUnit, StaticImportInspectorFromJar staticImportInspectorFromJar) {
        this.compilationUnit = compilationUnit;
        var imports = this.compilationUnit.findAll(ImportDeclaration.class).toArray(ImportDeclaration[]::new);
        this.fileImports = Arrays.stream(imports).toList();
        List<ImportClassPath>  importList = Arrays.stream(imports).map(classImport -> new ImportClassPath(classImport.toString().trim())).toList();
        var result = staticImportInspectorFromJar.getImportDetailsWithMultipleClassPath(importList);
        importDetails = result.a;
        failImports = result.b;
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
            fieldResolvedNode.getJavadoc().ifPresent(doc -> processJavadoc(node, doc, result, unableResolveImport));
        } else if (node instanceof VariableDeclarator variableResolvedNode) {
            processType(node, variableResolvedNode.getType(), result, unableResolveImport);
            variableResolvedNode.getInitializer().ifPresent(t -> {
                var tempResult = resolveNodeType(t);
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
            });
            processType(node, variableResolvedNode.getType(), result, unableResolveImport);
        } else if (node instanceof ClassOrInterfaceDeclaration classResolvedNode) {
            var extendTypes = classResolvedNode.getExtendedTypes();
            var implementTypes = classResolvedNode.getImplementedTypes();
            var permittedTypes = classResolvedNode.getPermittedTypes();


            extendTypes.forEach(types -> processType(node, types, result, unableResolveImport));
            implementTypes.forEach(types -> processType(node, types, result, unableResolveImport));
            permittedTypes.forEach(types -> processType(node, types, result, unableResolveImport));

            var tempResult = extractTypeParams(node, classResolvedNode.getTypeParameters());
            result.addAll(tempResult.a);
            unableResolveImport.addAll(tempResult.b);
            classResolvedNode.getJavadoc().ifPresent(doc -> processJavadoc(node, doc, result, unableResolveImport));
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
            processType(node, instanceOfExprResolvedNode.getType(), result, unableResolveImport);
        }  else if (node instanceof CastExpr castExprResolvedNode) {
            var tempResult = resolveNodeType(castExprResolvedNode.getExpression());
            result.addAll(tempResult.a);
            unableResolveImport.addAll(tempResult.b);
            processType(node, castExprResolvedNode.getType(), result, unableResolveImport);
        } else if (node instanceof Parameter parameterResolvedNode) {
            processType(node, parameterResolvedNode.getType(), result, unableResolveImport);
        } else if (node instanceof MethodDeclaration methodResolvedNode) {
            processType(node, methodResolvedNode.getType(), result, unableResolveImport);
            var typeParamResult = extractTypeParams(node, methodResolvedNode.getTypeParameters());
            result.addAll(typeParamResult.a);
            unableResolveImport.addAll(typeParamResult.b);
            methodResolvedNode.getJavadoc().ifPresent(doc -> processJavadoc(node, doc, result, unableResolveImport));
        } else if (node instanceof MethodCallExpr methodCallResolvedNode) {
            var methodArguments = methodCallResolvedNode.getArguments();
            var methodTypeArguments = methodCallResolvedNode.getTypeArguments();
            var methodName = methodCallResolvedNode.getNameAsString();
            var methodScope = methodCallResolvedNode.getScope();

            if (methodScope.isPresent()) {
                processScope(node, methodScope.get(), result, unableResolveImport);
            } else {
                addImportMethodToResult(node, methodName, result, unableResolveImport);
            }

            if (methodTypeArguments.isPresent()) {
                var types = methodTypeArguments.get().stream().flatMap(t -> {
                    checkFullPathImport.add(t.toString());
                    return MyTypeSolvers.splitStructuredTypes(t.toString()).stream();
                }).toList();
                types.forEach(type -> addImportClassToResult(node, type, result, unableResolveImport));
            }

            methodArguments.forEach(m -> {
                var tempResult = resolveNodeType(m);
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
            });
        } else if (node instanceof NameExpr nameExprResolvedNode) {
            var name = nameExprResolvedNode.getName();
            addImportFieldToResult(node, name.toString(), result, unableResolveImport);
        } else if (node instanceof EnumDeclaration enumResolvedNode) {
            var implementType = enumResolvedNode.getImplementedTypes();
            var extractedImplementType = implementType.stream().flatMap(t -> {
                checkFullPathImport.add(t.toString());
                return MyTypeSolvers.splitStructuredTypes(t.toString()).stream();
            }).toList();
            extractedImplementType.forEach(type -> {
                addImportClassToResult(node, type, result, unableResolveImport);
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
            annotationType.forEach(a -> addImportClassToResult(node, a, result, unableResolveImport));
            if (annotationExprResolvedNode.isSingleMemberAnnotationExpr()) {
                var singleMemberAnnotation = annotationExprResolvedNode.asSingleMemberAnnotationExpr();
                var tempResult = resolveNodeType(singleMemberAnnotation.getMemberValue());
                result.addAll(tempResult.a);
                unableResolveImport.addAll(tempResult.b);
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
            processType(node, annotationMemberDeclarationResolvedNode.getType(), result, unableResolveImport);
        } else if (node instanceof FieldAccessExpr fieldAccessExprResolvedNode) {
            processScope(node, fieldAccessExprResolvedNode.getScope(), result, unableResolveImport);
            var typeArguments = fieldAccessExprResolvedNode.getTypeArguments();
            typeArguments.ifPresent(types -> types.forEach(t -> processType(node, t, result, unableResolveImport)));
        } else if (node instanceof ObjectCreationExpr objectCreationExprResolvedNode) {;
            processType(node, objectCreationExprResolvedNode.getType(), result, unableResolveImport);
            processInnerType(objectCreationExprResolvedNode.getArguments(), result, unableResolveImport);
            var typeArguments = objectCreationExprResolvedNode.getTypeArguments();
            typeArguments.ifPresent(types -> types.forEach(t -> processType(node, t, result, unableResolveImport)));
            var scope = objectCreationExprResolvedNode.getScope();
            scope.ifPresent(s -> processScope(node, s, result, unableResolveImport));
        } else if (node instanceof MethodReferenceExpr methodReferenceExprResolvedNode) {
            var typeArguments = methodReferenceExprResolvedNode.getTypeArguments();
            typeArguments.ifPresent(types -> types.forEach(t -> processType(node, t, result, unableResolveImport)));
            processScope(node, methodReferenceExprResolvedNode.getScope(), result, unableResolveImport);
        } else if (node instanceof TypePatternExpr typePatternExprResolvedNode) {
            processType(node, typePatternExprResolvedNode.getType(), result, unableResolveImport);
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
            processType(node, patternExprResolvedNode.getType(), result, unableResolveImport);
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
            processType(node, classExprResolvedNode.getType(), result, unableResolveImport);
        }
        else if (node instanceof CatchClause catchClauseResolvedNode) {
            processInnerType(new NodeList<>(catchClauseResolvedNode.getParameter()), result, unableResolveImport);
        }
        else if (node instanceof ReferenceType referenceTypeResolvedNode) {
            processTypeStr(node, referenceTypeResolvedNode.toString(), result, unableResolveImport);
        } else if (node instanceof Type type) {
            processType(node, type, result, unableResolveImport);
        }
        return new Pair<>(result, unableResolveImport);
    }

    private void processTypeStr(Node node,String typeStr, List<SingleImportDetails> result, List<String> unableResolveImport) {
        checkFullPathCalling.add(typeStr);
        var types = MyTypeSolvers.splitStructuredTypes(typeStr);
        types.forEach(type -> {
            addImportClassToResult(node, type, result, unableResolveImport);
        });
    }

    private <T extends Node> void processInnerType(NodeList<T> expressions, List<SingleImportDetails> result, List<String> unableResolveImport) {
        expressions.forEach(e -> {
            var tempResult = resolveNodeType(e);
            result.addAll(tempResult.a);
            unableResolveImport.addAll(tempResult.b);
        });
    }

    private void processScope(Node node, Expression scope, List<SingleImportDetails> result, List<String> unableResolveImport) {
        // Check static object call
        checkFullPathCalling.add(scope.toString());
        MyTypeSolvers.splitStructuredTypes(scope.toString()).forEach(t -> addImportClassToResult(node, t, result, unableResolveImport));

        // Check inner call
        var tempResult = resolveNodeType(scope);
        result.addAll(tempResult.a);
        unableResolveImport.addAll(tempResult.b);
    }

    private void processType(Node node, Type variableResolvedNode, List<SingleImportDetails> result, List<String> unableResolveImport) {
        checkFullPathCalling.add(variableResolvedNode.toString());
        var types = MyTypeSolvers.splitStructuredTypes(variableResolvedNode.asString());
        types.forEach(type -> {
            addImportClassToResult(node, type, result, unableResolveImport);
        });
    }

    private void addImportFieldToResult(Node node, String field, List<SingleImportDetails> result, List<String> unableResolveImport) {
        try {
            result.add(importDetails.getField(field));
        } catch (Exception e) {
            unableResolveImport.add(field);
        }
    }

    private void addImportMethodToResult(Node node, String method, List<SingleImportDetails> result, List<String> unableResolveImport) {
        if (isMethodDeclareInParentScope(node, method)) {
            return;
        }
        try {
            result.add(importDetails.getMethod(method));
        } catch (Exception e) {
            unableResolveImport.add(method);
        }
    }

    private void addImportClassToResult(Node node, String type, List<SingleImportDetails> result, List<String> unableResolveImport) {
        if (isClassDeclareInParentScope(node, type)) {
            return;
        }
        try {
            result.add(importDetails.getClass(type));
        } catch (Exception e) {
            unableResolveImport.add(type);
        }
    }

    private Boolean isClassDeclareInParentScope(Node node, String className) {
        Node currentNode = node;
        while (currentNode.hasParentNode() && currentNode.getParentNode().isPresent()) {
            currentNode = currentNode.getParentNode().get();
            if (isClassShadowedInNode(currentNode, className)) {
                return true;
            }
        }
        return false;
    }

    private Boolean isClassShadowedInNode(Node node, String className) {
        for (Node childNode : node.getChildNodes()) {
            if (childNode instanceof ClassOrInterfaceDeclaration innerClass) {
                if (innerClass.getNameAsString().equals(className)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Boolean isMethodDeclareInParentScope(Node node, String className) {
        Node currentNode = node;
        while (currentNode.hasParentNode() && currentNode.getParentNode().isPresent()) {
            currentNode = currentNode.getParentNode().get();
            if (isMethodShadowedInNode(currentNode, className)) {
                return true;
            }
        }
        return false;
    }

    private Boolean isMethodShadowedInNode(Node node, String className) {
        for (Node childNode : node.getChildNodes()) {
            if (childNode instanceof MethodDeclaration innerClass) {
                if (innerClass.getNameAsString().equals(className)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Node getScope(Node node) {
        var currentNode = node;
        while (currentNode.getParentNode().isPresent()) {
            currentNode = currentNode.getParentNode().get();
            if (currentNode instanceof ClassOrInterfaceDeclaration ||
                currentNode instanceof EnumDeclaration ||
                currentNode instanceof RecordDeclaration ||
                currentNode instanceof MethodDeclaration ||
                currentNode instanceof ConstructorDeclaration ||
                currentNode instanceof BlockStmt ||
                currentNode instanceof LambdaExpr) {
                return currentNode;
            }
        }
        return null;
    }

    private Boolean isFieldDeclareInParentScope(Node node, String className) {
        Node currentNode = node;
        if (node.getRange().isEmpty()) {
            return false;
        }
        Integer lineNumber = node.getRange().get().begin.line;
        if (node.getParentNode().isEmpty()) {
            return false;
        }
        var fieldScope = getScope(node);
        if (!Objects.equals(node.toString(), "PI")) {
            return false;
        }
        System.out.println(fieldScope);
        currentNode = node.getParentNode().get();
        while (currentNode.hasParentNode() && currentNode.getParentNode().isPresent()) {
            currentNode = currentNode.getParentNode().get();
            if (isFieldShadowedInNode(currentNode, className, lineNumber, fieldScope)) {
                return true;
            }
        }
        return false;
    }

    public Boolean isFieldShadowedInNode(Node node, String fieldName, Integer lineNumber, Node fieldScope) {
        for (Node childNode : node.getChildNodes()) {
            if (childNode instanceof FieldDeclaration innerField) {
                for (VariableDeclarator variable : innerField.getVariables()) {
                    if (variable.getNameAsString().equals(fieldName)) {
                        return true;
                    }
                }
            } else if (childNode instanceof VariableDeclarationExpr variableExpr) {
                var childNodeScope = getScope(childNode);
                if (childNodeScope == fieldScope) {
                    if (variableExpr.getRange().isPresent()) {
                        var innerFieldLineNumber = variableExpr.getRange().get().begin.line;
                        for (VariableDeclarator variable : variableExpr.getVariables()) {
                            if (variable.getNameAsString().equals(fieldName) && innerFieldLineNumber < lineNumber) {
                                return true;
                            }
                        }
                    }
                } else {
                    if (variableExpr.getRange().isPresent()) {
                        for (VariableDeclarator innerVariable : variableExpr.getVariables()) {
                            if (innerVariable.getNameAsString().equals(fieldName)) {
                                return true;
                            }
                        }
                    }
                }
            } else if (childNode instanceof ExpressionStmt innerExpression) {
                var childNodeScope = getScope(childNode);
                if (childNodeScope == fieldScope) {
                    if (innerExpression.getRange().isPresent()) {
                        var innerFieldLineNumber = innerExpression.getRange().get().begin.line;
                        var expression = innerExpression.getExpression();
                        if (expression instanceof VariableDeclarationExpr variable) {
                            for (VariableDeclarator innerVariable : variable.getVariables()) {
                                if (innerVariable.getNameAsString().equals(fieldName) && innerFieldLineNumber < lineNumber) {
                                    return true;
                                }
                            }
                        }
                    }
                } else {
                    var expression = innerExpression.getExpression();
                    if (expression instanceof VariableDeclarationExpr variable) {
                        for (VariableDeclarator innerVariable : variable.getVariables()) {
                            if (innerVariable.getNameAsString().equals(fieldName)) {
                                return true;
                            }
                        }
                    }
                }

            }
        }
        if (node instanceof MethodDeclaration methodDeclaration) {
            for (Parameter parameter : methodDeclaration.getParameters()) {
                if (parameter.getNameAsString().equals(fieldName)) {
                    return true;
                }
            }
        } else if (node instanceof ConstructorDeclaration constructorDeclaration) {
            for (Parameter parameter : constructorDeclaration.getParameters()) {
                if (parameter.getNameAsString().equals(fieldName)) {
                    return true;
                }
            }
        } else if (node instanceof RecordDeclaration recordDeclaration) {
            for (Parameter parameter : recordDeclaration.getParameters()) {
                if (parameter.getNameAsString().equals(fieldName)) {
                    return true;
                }
            }
        } else if (node instanceof LambdaExpr lambdaExpr) {
            for (Parameter parameter : lambdaExpr.getParameters()) {
                if (parameter.getNameAsString().equals(fieldName)) {
                    return true;
                }
            }
        }

        return false;
    }

//    private Boolean isFieldShadowedInNode(Node node, String fieldName) {
//        for (Node childNode : node.getChildNodes()) {
//            if (childNode instanceof FieldDeclaration innerField) {
//                if (innerField.getRange().isPresent()) {
//                    var innerFieldLineNumber = innerField.getRange().get().begin.line;
//                    if (innerFieldLineNumber < lineNumber &&) {
//
//                    }
//                }
//
//            }
//        }
//        return false;
//    }

    public Pair<List<SingleImportDetails>, List<String>> extractTypeParams(Node node, NodeList<TypeParameter> typeParameters) {
        List<SingleImportDetails> result = new ArrayList<>();
        List<String> unableResolveImport = new ArrayList<>();
        typeParameters.forEach(typeParameter -> {
            var types = typeParameter.getTypeBound().stream().flatMap(t -> MyTypeSolvers.splitStructuredTypes(t.toString()).stream()).toList();
            types.forEach(type -> addImportClassToResult(node, type, result, unableResolveImport));
        });
        return new Pair<>(result, unableResolveImport);
    }

    public List<ImportClassPath> getFailImports() {
        return failImports;
    }

    public void processJavadoc(Node node, Javadoc javadoc, List<SingleImportDetails> result, List<String> unableResolveImport) {
        List<JavadocBlockTag> blockTags = javadoc.getBlockTags();
        blockTags.forEach(blockTag -> {
            switch (blockTag.getType()) {
                case SEE -> {
                    var elements = blockTag.getContent().getElements();
                    JavadocSnippet snippet = elements.stream().filter(e -> e instanceof JavadocSnippet).map(e -> (JavadocSnippet) e).findFirst().orElse(null);
                    if (snippet != null) {
                        var functionCall = snippet.toText().split(" ")[0].trim();
                        var className = functionCall.split("#")[0];
                        processTypeStr(node, className, result, unableResolveImport);
                    }
                    processJavadocDescriptionElement(node, elements, result, unableResolveImport);
                }
                case EXCEPTION, THROWS -> {
                    var className = blockTag.getName().orElse("");
                    processTypeStr(node, className, result, unableResolveImport);
                    var elements = blockTag.getContent().getElements();
                    processJavadocDescriptionElement(node, elements, result, unableResolveImport);

                }
            }
        });
        var javaDocElement = javadoc.getDescription().getElements();
        processJavadocDescriptionElement(node, javaDocElement, result, unableResolveImport);
    }

    public void processJavadocDescriptionElement(Node node, List<JavadocDescriptionElement> javadocDescriptionElements, List<SingleImportDetails> result, List<String> unableResolveImport) {
        String regex = "\\(([^)]+)\\)";
        Pattern pattern = Pattern.compile(regex);

        javadocDescriptionElements.forEach(e -> {
            if (e instanceof JavadocInlineTag javaDocInlineTag) {
                if (javaDocInlineTag.getType() ==  JavadocInlineTag.Type.LINK) {
                    if (javaDocInlineTag.getContent().contains("#")) {
                        var splitContent = javaDocInlineTag.getContent().split("#", 2);
                        var className = splitContent[0].trim();
                        var member = splitContent[1].trim();
                        processTypeStr(node, className, result, unableResolveImport);
                        if (member.contains("(") && member.contains(")")) {
                            Matcher matcher = pattern.matcher(splitContent[1]);
                            if (matcher.find()) {
                                String parameters = matcher.group(1);
                                Arrays.stream(parameters.split(",")).forEach(f -> {
                                    processTypeStr(node, f, result, unableResolveImport);
                                });
                            }
                        }
                    } else {
                        var className = javaDocInlineTag.getContent().trim();
                        processTypeStr(node, className, result, unableResolveImport);
                    }
                }
            }
        });
    }
}
