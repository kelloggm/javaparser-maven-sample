package com.martinjkellogg.arraylistanalysis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.CodeGenerationUtils;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Some code that uses JavaParser.
 */
public class ArrayListAnalyzer {

    public static void main(String[] args) throws IOException {

        // Set up a minimal type solver that only looks at the classes used to run this sample.
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());

        // Configure JavaParser to use type resolution
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);

        // SourceRoot is a tool that read and writes Java files from packages on a certain root directory.
        // In this case the root directory is found by taking the root from the current Maven module,
        // with src/main/resources appended.
        SourceRoot sourceRoot = new SourceRoot(CodeGenerationUtils.mavenModuleRoot(ArrayListAnalyzer.class).resolve("src/main/resources"));

        sourceRoot.getParserConfiguration().setSymbolResolver(symbolSolver);

        // Our sample is in the root of this directory, so no package name.
        CompilationUnit cu = sourceRoot.parse("", "SimpleYesTransform.java");

        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(BlockStmt n, Void arg) {
                List<Node> forEachNodes =
                        n.getChildNodes().stream().filter(n1 -> n1 instanceof ForEachStmt).collect(Collectors.toList());

                for (Node node : forEachNodes) {
                    // Need to do a series of checks to prove that the replacement is safe:
                    // 1. the thing being looped over must be an array list
                    // 2. the array list must be locally-scoped
                    // 3. the value of the array list must *only* come from a call to the array list constructor
                    if (!isLocalArrayList(node, n)) {
                        continue;
                    }

                    if (callsModificationMethods(node)) {
                        continue;
                    }

                    doReplace(n, node);
                }

                return super.visit(n, arg);
            }
        }, null);

        // This saves all the files we just read to an output directory.  
        sourceRoot.saveAll(
                // The path of the Maven module/project which contains the class.
                CodeGenerationUtils.mavenModuleRoot(ArrayListAnalyzer.class)
                        // appended with a path to "output"
                        .resolve(Paths.get("output")));
    }

    private static boolean isLocalArrayList(Node node, BlockStmt n) {
        ForEachStmt forEachNode = (ForEachStmt) node;

        Expression iterable = forEachNode.getIterable();

        if (!iterable.isNameExpr()) {
            return false;
        }

        NameExpr iterableName = iterable.asNameExpr();

        ResolvedType resolvedType = iterable.calculateResolvedType();

        if (!resolvedType.isReferenceType()) {
            return false;
        }

        ResolvedReferenceType refType = resolvedType.asReferenceType();
        if (!("java.util.ArrayList".equals(refType.getQualifiedName()) ||
                "java.util.List".equals(refType.getQualifiedName()))) {
            return false;
        }


        List<AssignExpr> allAssignments = n.findAll(AssignExpr.class);
        List<VariableDeclarationExpr> allDecls = n.findAll(VariableDeclarationExpr.class);
        
        // if there were no declarations in the method body, then the foreach loop can't be over a local variable
        if (allDecls.size() == 0) {
            return false;
        }

        boolean wasDeclaredLocally = false;
        // if it wasn't declared locally, reject
        for (VariableDeclarationExpr declExpr : allDecls) {
            for (VariableDeclarator declarator : declExpr.getVariables()) {
                if (declarator.getName().equals(iterableName.getName())) {
                    if (declarator.getInitializer().isPresent() &&
                            declarator.getInitializer().get().isObjectCreationExpr()) {

                        ObjectCreationExpr declLhs = declarator.getInitializer().get().asObjectCreationExpr();

                        if ("ArrayList".equals(declLhs.getType().getName().toString())) {
                            wasDeclaredLocally = true;
                        }
                    }
                } else if (declarator.getInitializer().isPresent() &&
                    declarator.getInitializer().get().isNameExpr()) {
                    // if a variable is assigned with the value of the iterable, reject the code
                    // because its being aliased
                    NameExpr nameExprLhs = declarator.getInitializer().get().asNameExpr();
                    if (nameExprLhs.getName().equals(iterableName.getName())) {
                        return false;
                    }
                }
            }
        }

        if (!wasDeclaredLocally) {
            return false;
        }

        // Check for aliases
        // Note that I have to use a foreach loop instead of a lambda here b/c I need to be able to return
        for (AssignExpr assignExpr : allAssignments) {
            Expression rhs = assignExpr.getTarget();

            // if the right hand side is a name expression containing the variable of interest, reject.
            // We're only looking for effectively final locals
            if (rhs.isNameExpr()) {
                NameExpr rhsAsName = rhs.asNameExpr();
                if (rhsAsName.getName().equals(iterableName.getName())) {
                    return false;
                }
            }

            Expression lhs = assignExpr.getValue();
            // if the variable of interest ever appears on the lhs of an assignment, reject. Possibly alias.
            if (lhs.isNameExpr()) {
                NameExpr lhsAsName = lhs.asNameExpr();
                if (lhsAsName.getName().equals(iterableName.getName())) {
                    return false;
                }
            }
        }

        // if a parameter to a method call is the list anywhere, reject because it might alias it.
        List<MethodCallExpr> methodCalls = n.findAll(MethodCallExpr.class);
        for (MethodCallExpr methodCallExpr : methodCalls) {
            for(Expression argument : methodCallExpr.getArguments()) {
                if (argument.isNameExpr()) {
                    NameExpr argAsName = argument.asNameExpr();
                    if (argAsName.getName().equals(iterableName.getName())) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static final String[] bannedMethods = {
            "add", "addAll", "remove", "removeAll", "removeIf", "removeRange", "retainAll"};
    private static final Set<String> bannedMethodsSet = new HashSet<String>();
    static {
        bannedMethodsSet.addAll(Arrays.asList(bannedMethods));
    }

    private static boolean callsModificationMethods(Node stmt) {
        ForEachStmt forEachStmt = (ForEachStmt) stmt;

        for (MethodCallExpr methodCall : forEachStmt.findAll(MethodCallExpr.class)) {
            if (bannedMethodsSet.contains(methodCall.getName().toString())) {
                return true;
            }
        }

        return false;
    }

    private static void doReplace(BlockStmt n, Node node) {

        // the level of evil here is very high, but this totally works lol
        // the default String representation of a UUID separates the different parts with dashes. That
        // violates the Java identifier rules, so get rid of those.
        // gotta start with "lv" to avoid starting an identifier with a number, also.
        final String loopvarName = "lv" + UUID.randomUUID().toString().replaceAll("-", "");

        ForEachStmt forEachStmt = (ForEachStmt) node;
        String listName = forEachStmt.getIterable().asNameExpr().getNameAsString();
        VariableDeclarationExpr varDecl = forEachStmt.getVariable().clone();

        ForStmt noIteratorStmt = new ForStmt();

        VariableDeclarationExpr declarator = new VariableDeclarationExpr(PrimitiveType.intType(), loopvarName);
        IntegerLiteralExpr literallyZero = new IntegerLiteralExpr(0);
        AssignExpr assignExpr = new AssignExpr(declarator, literallyZero, AssignExpr.Operator.ASSIGN);
        NodeList<Expression> initalizer = new NodeList<>(assignExpr);
        noIteratorStmt.setInitialization(initalizer);

        BinaryExpr comparison = new BinaryExpr(new NameExpr(loopvarName),
                new MethodCallExpr(new NameExpr(listName), new SimpleName("size")),
                BinaryExpr.Operator.LESS);

        noIteratorStmt.setCompare(comparison);

        AssignExpr updateExpr = new AssignExpr(new NameExpr(loopvarName),
                new IntegerLiteralExpr(1), AssignExpr.Operator.PLUS);
        NodeList<Expression> update = new NodeList<>(updateExpr);
        noIteratorStmt.setUpdate(update);

        Statement body = forEachStmt.getBody().clone();
        NodeList<Expression> args = new NodeList<>(new NameExpr(loopvarName));
        AssignExpr assignExpr1 = new AssignExpr(varDecl,
                new MethodCallExpr(new NameExpr(listName), new SimpleName("get"), args),
                AssignExpr.Operator.ASSIGN);

        BlockStmt newBody = new BlockStmt();
        newBody.addStatement(assignExpr1);

        for (Statement statement : body.asBlockStmt().getStatements()) {
            newBody.addStatement(statement);
        }

        noIteratorStmt.setBody(newBody);

        noIteratorStmt.setComment(forEachStmt.getComment().isPresent() ? forEachStmt.getComment().get() : null);

        n.replace(forEachStmt, noIteratorStmt);
    }
}
