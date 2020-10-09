package randoop.util;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class VariableModifier extends VoidVisitorAdapter<Void> {
    @Override
    public void visit(MethodDeclaration md, Void arg) {
        super.visit(md, arg);

        if (md.getBody().isPresent()) {
            md.getBody().get().getStatements().forEach(statement -> {
                if (statement.isExpressionStmt()) {
                    if (statement.asExpressionStmt().getExpression().isVariableDeclarationExpr()) {
                        VariableDeclarationExpr v = statement.asExpressionStmt().getExpression().asVariableDeclarationExpr();
                        v.getVariables().forEach(varDecl -> {
                            varDecl.getInitializer().ifPresent(initializer -> {
                                if (initializer.isObjectCreationExpr()) {
                                    initializer.asObjectCreationExpr().getArguments().forEach(argOfConstructor -> {
                                        if (argOfConstructor.isEnclosedExpr()) {
                                            argOfConstructor.asEnclosedExpr().getInner().ifUnaryExpr(unaryExpr -> {
                                                if (unaryExpr.asUnaryExpr().getOperator() == UnaryExpr.Operator.MINUS) {
                                                    unaryExpr.getExpression().ifIntegerLiteralExpr(integer -> {
                                                        IntegerLiteralExpr argument = new IntegerLiteralExpr(-integer.asIntegerLiteralExpr().asInt() + 2);
                                                        varDecl.setInitializer(initializer.asObjectCreationExpr().setArgument(0, argument));
                                                    });
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        });
                    }
                }
            });
        }
    }
}