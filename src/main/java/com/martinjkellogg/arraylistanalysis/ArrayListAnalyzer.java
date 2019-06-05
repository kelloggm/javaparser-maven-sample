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
import com.github.javaparser.utils.CodeGenerationUtils;
import com.github.javaparser.utils.SourceRoot;
import sun.jvm.hotspot.opto.Block;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Some code that uses JavaParser.
 */
public class ArrayListAnalyzer {

    public static void main(String[] args) throws IOException {
        // SourceRoot is a tool that read and writes Java files from packages on a certain root directory.
        // In this case the root directory is found by taking the root from the current Maven module,
        // with src/main/resources appended.
        SourceRoot sourceRoot = new SourceRoot(CodeGenerationUtils.mavenModuleRoot(ArrayListAnalyzer.class).resolve("src/main/resources"));

        // Our sample is in the root of this directory, so no package name.
        CompilationUnit cu = sourceRoot.parse("", "SimpleYesTransform.java");

        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(BlockStmt n, Void arg) {
                List<Node> forEachNodes =
                        n.getChildNodes().stream().filter(n1 -> n1 instanceof ForEachStmt).collect(Collectors.toList());

                for (Node node : forEachNodes) {

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

                    n.replace(forEachStmt, noIteratorStmt);
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
}
