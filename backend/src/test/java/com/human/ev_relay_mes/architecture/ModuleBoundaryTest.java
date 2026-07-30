package com.human.ev_relay_mes.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleBoundaryTest {

    private static final Path MAIN_SOURCE_ROOT =
            Path.of("src", "main", "java", "com", "human", "ev_relay_mes");
    private static final Path FEATURE_ROOT = MAIN_SOURCE_ROOT.resolve("feature");
    private static final Path COMMON_ROOT = MAIN_SOURCE_ROOT.resolve("common");
    private static final Set<String> PLANNED_MODULES = Set.of(
            "auth",
            "notice",
            "notification",
            "masterdata",
            "material",
            "workforce",
            "machine",
            "quality",
            "production",
            "collector",
            "dashboard");
    private static final Set<String> INTERNAL_SUPPORT_SUFFIXES = Set.of(
            "Factory",
            "Policy",
            "Assembler",
            "Calculator",
            "Allocator",
            "Resolver",
            "Coordinator",
            "Dispatcher",
            "Processor",
            "Recovery",
            "Rules",
            "Manager");
    private static final Pattern FEATURE_IMPORT = Pattern.compile(
            "^import\\s+(?:static\\s+)?com\\.human\\.ev_relay_mes\\.feature"
                    + "\\.([a-z0-9_]+)\\.([a-z0-9_.]+);\\s*$",
            Pattern.MULTILINE);
    private static final Pattern INTERNAL_FEATURE_IMPORT = Pattern.compile(
            "^import\\s+(?:static\\s+)?com\\.human\\.ev_relay_mes\\.feature"
                    + "\\.([a-z0-9_]+)\\.internal(?:\\.[a-zA-Z0-9_.*]+)?;\\s*$",
            Pattern.MULTILINE);

    @Test
    void plannedFeatureModuleRootsExist() throws IOException {
        Set<String> actualModules;
        try (Stream<Path> paths = Files.list(FEATURE_ROOT)) {
            actualModules = paths
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
        }

        assertThat(actualModules).containsExactlyInAnyOrderElementsOf(PLANNED_MODULES);
    }

    @Test
    void featureModulesOnlyImportAnotherModulesPublicApi() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSourcesUnder(FEATURE_ROOT)) {
            String currentModule = FEATURE_ROOT.relativize(source).getName(0).toString();
            Matcher imports = FEATURE_IMPORT.matcher(Files.readString(source));
            while (imports.find()) {
                String targetModule = imports.group(1);
                String targetPackage = imports.group(2);
                if (!currentModule.equals(targetModule)
                        && !targetPackage.equals("api")
                        && !targetPackage.startsWith("api.")) {
                    violations.add(relative(source) + " imports "
                            + targetModule + "." + targetPackage);
                }
            }
        }

        assertThat(violations)
                .as("Cross-module imports must target feature.<module>.api")
                .isEmpty();
    }

    @Test
    void featureCodeLivesInApiOrInternalPackages() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSourcesUnder(FEATURE_ROOT)) {
            Path moduleRelativePath = FEATURE_ROOT.relativize(source);
            if (moduleRelativePath.getNameCount() < 3) {
                violations.add(relative(source));
                continue;
            }
            String boundaryPackage = moduleRelativePath.getName(1).toString();
            if (!boundaryPackage.equals("api") && !boundaryPackage.equals("internal")) {
                violations.add(relative(source));
            }
        }

        assertThat(violations)
                .as("Feature code must live below feature.<module>.api or .internal")
                .isEmpty();
    }

    @Test
    void publicApiDoesNotDependOnInternalImplementation() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSourcesUnder(FEATURE_ROOT)) {
            Path moduleRelativePath = FEATURE_ROOT.relativize(source);
            if (moduleRelativePath.getNameCount() < 3
                    || !moduleRelativePath.getName(1).toString().equals("api")) {
                continue;
            }
            String content = Files.readString(source);
            if (content.matches("(?s).*com\\.human\\.ev_relay_mes\\.feature"
                    + "\\.[a-z0-9_]+\\.internal(?:\\.|;).*")) {
                violations.add(relative(source));
            }
        }

        assertThat(violations)
                .as("Public feature APIs must not depend on internal implementation")
                .isEmpty();
    }

    @Test
    void codeOutsideFeatureModuleDoesNotImportItsInternalImplementation() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSourcesUnder(MAIN_SOURCE_ROOT)) {
            Path relativePath = MAIN_SOURCE_ROOT.relativize(source);
            String currentModule = relativePath.getNameCount() >= 2
                    && relativePath.getName(0).toString().equals("feature")
                    ? relativePath.getName(1).toString()
                    : null;
            Matcher imports = INTERNAL_FEATURE_IMPORT.matcher(Files.readString(source));
            while (imports.find()) {
                String targetModule = imports.group(1);
                if (!targetModule.equals(currentModule)) {
                    violations.add(relative(source) + " imports "
                            + targetModule + ".internal");
                }
            }
        }

        assertThat(violations)
                .as("Only a feature module may import its own internal implementation")
                .isEmpty();
    }

    @Test
    void commonDoesNotDependOnFeatureModules() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSourcesUnder(COMMON_ROOT)) {
            String content = Files.readString(source);
            if (content.contains("com.human.ev_relay_mes.feature.")) {
                violations.add(relative(source));
            }
        }

        assertThat(violations)
                .as("common must remain independent from feature modules")
                .isEmpty();
    }

    @Test
    void focusedInternalSupportTypesStayPackagePrivate() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSourcesUnder(FEATURE_ROOT)) {
            Path moduleRelativePath = FEATURE_ROOT.relativize(source);
            if (moduleRelativePath.getNameCount() < 4
                    || !moduleRelativePath.getName(1).toString().equals("internal")
                    || !moduleRelativePath.getName(2).toString().equals("service")) {
                continue;
            }
            String fileName = source.getFileName().toString();
            String typeName = fileName.substring(0, fileName.length() - ".java".length());
            boolean focusedSupportType = INTERNAL_SUPPORT_SUFFIXES.stream()
                    .anyMatch(typeName::endsWith);
            if (focusedSupportType && Files.readString(source)
                    .matches("(?s).*\\bpublic\\s+(?:final\\s+)?"
                            + "(?:class|record|interface|enum)\\s+"
                            + Pattern.quote(typeName) + "\\b.*")) {
                violations.add(relative(source));
            }
        }

        assertThat(violations)
                .as("Focused internal support types should not leak as public APIs")
                .isEmpty();
    }

    private List<Path> javaSourcesUnder(Path root) throws IOException {
        if (Files.notExists(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private String relative(Path source) {
        return MAIN_SOURCE_ROOT.relativize(source).toString().replace('\\', '/');
    }
}
