import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Week10 {

    public static List<String> getAllFunctions(String contentFile) throws Exception {
        List<String> methodSignatures = new ArrayList<>();

        CharStream input = CharStreams.fromString(contentFile);
        Java20Lexer lexer = new Java20Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Java20Parser parser = new Java20Parser(tokens);

        Java20Parser.CompilationUnitContext compilationUnit = parser.compilationUnit();

        String packageName = "default";
        if (compilationUnit.ordinaryCompilationUnit().packageDeclaration() != null) {
            Java20Parser.PackageDeclarationContext packageContext = compilationUnit.ordinaryCompilationUnit().packageDeclaration();
            StringBuilder packageBuilder = new StringBuilder();
            for (int i = 0; i < packageContext.identifier().size(); i++) {
                packageBuilder.append(packageContext.identifier(i).getText());
                if (i < packageContext.identifier().size() - 1) {
                    packageBuilder.append(".");
                }
            }
            packageName = packageBuilder.toString();
        }

        Map<String, String> imports = new HashMap<>();
        for (Java20Parser.ImportDeclarationContext importContext : compilationUnit.ordinaryCompilationUnit().importDeclaration()) {
            if (importContext.singleTypeImportDeclaration() != null) {
                String importPath = importContext.singleTypeImportDeclaration().getText().replace(";", "").replace("import", "").trim();
                String shortName = importPath.substring(importPath.lastIndexOf('.') + 1);
                imports.put(shortName, importPath);
            } else if (importContext.typeImportOnDemandDeclaration() != null) {
                String packagePath = importContext.typeImportOnDemandDeclaration().getText().replace(".*;", "");
                imports.put(packagePath + ".*", packagePath);
            }
        }

        ParseTreeWalker walker = new ParseTreeWalker();
        String finalPackageName = packageName;
        walker.walk(new Java20ParserBaseListener() {
            @Override
            public void enterMethodDeclaration(Java20Parser.MethodDeclarationContext context) {
                List<Java20Parser.MethodModifierContext> modifiers = context.methodModifier();
                boolean isStatic = false;
                for (Java20Parser.MethodModifierContext modifier : modifiers) {
                    if (modifier.getText().equals("static")) {
                        isStatic = true;
                    }
                }

                if (!isStatic) {
                    return;
                }

                String methodName = context.methodHeader().methodDeclarator().identifier().getText();
                StringBuilder signature = new StringBuilder(methodName);

                Java20Parser.MethodDeclaratorContext declarator = context.methodHeader().methodDeclarator();
                if (declarator.formalParameterList() != null) {
                    Java20Parser.FormalParameterListContext parameterList = declarator.formalParameterList();
                    signature.append("(");
                    for (int i = 0; i < parameterList.formalParameter().size(); i++) {
                        Java20Parser.FormalParameterContext param = parameterList.formalParameter(i);
                        String type;
                        if (param.unannType() != null) {
                            type = resolveFullyQualifiedType(param.unannType().getText(), imports, finalPackageName);
                        } else if (param.variableArityParameter() != null) {
                            type = resolveFullyQualifiedType(param.variableArityParameter().unannType().getText(), imports, finalPackageName);
                        } else {
                            type = "unknown";
                        }
                        signature.append(type);

                        if (i < parameterList.formalParameter().size() - 1) {
                            signature.append(", ");
                        }
                    }
                    signature.append(")");
                } else {
                    signature.append("()");
                }

                methodSignatures.add(signature.toString());
            }

            private String resolveFullyQualifiedType(String type, Map<String, String> imports, String currentPackage) {
                if (isPrimitiveType(type)) {
                    return type;
                }
                if (isJavaLangType(type)) {
                    return "java.lang." + type;
                }
                if (imports.containsKey(type)) {
                    return imports.get(type);
                }
                for (Map.Entry<String, String> entry : imports.entrySet()) {
                    if (entry.getKey().endsWith(".*")) {
                        return entry.getValue() + "." + type;
                    }
                }
                return currentPackage + "." + type;
            }

            private boolean isJavaLangType(String type) {
                return List.of("String", "Integer", "Double", "Boolean", "Object", "Class<?>").contains(type);
            }

            private boolean isPrimitiveType(String type) {
                return List.of("byte", "short", "int", "long", "float", "double", "char", "boolean").contains(type);
            }
        }, compilationUnit);

        return methodSignatures;
    }

    public static void main(String[] args) {
        String filePath = "sample/DatabaseUtils.java";
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            List<String> a = getAllFunctions(content.toString());
            for (String s : a) {
                System.out.println(s);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
