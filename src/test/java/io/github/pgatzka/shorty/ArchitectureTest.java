package io.github.pgatzka.shorty;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Module boundaries (Spring Modulith) and layer rules inside the modules (ArchUnit). */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.github.pgatzka.shorty");
    }

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(ShortyApplication.class).verify();
    }

    @Test
    void layersOnlyDependDownwards() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("View")
                .definedBy("..view..", "..web..")
                .layer("Service")
                .definedBy("..service..")
                .layer("Repository")
                .definedBy("..repository..")
                .layer("Domain")
                .definedBy("..domain..")
                .whereLayer("View")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Service")
                .mayOnlyBeAccessedByLayers("View")
                .whereLayer("Repository")
                .mayOnlyBeAccessedByLayers("Service")
                .whereLayer("Domain")
                .mayOnlyBeAccessedByLayers("View", "Service", "Repository")
                .check(classes);
    }

    @Test
    void viewsNeverTouchRepositories() {
        noClasses()
                .that()
                .resideInAnyPackage("..view..", "..web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..repository..")
                .check(classes);
    }

    @Test
    void domainIsFrameworkFree() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .and()
                .doNotHaveSimpleName("package-info")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "com.vaadin..", "com.google..")
                .check(classes);
    }
}
