package io.roseiot;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.mybatisplus.annotation.TableName;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTest {

    @Test
    void applicationArchitectureIsRespected() {
        var applicationClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("io.roseiot");

        ArchRule noFeatureCycles = slices().matching("io.roseiot.(*)..")
                .should().beFreeOfCycles()
                .because("top-level feature and shared package boundaries must remain acyclic");
        ArchRule noIamFeatureCycles = slices().matching("io.roseiot.iam.(*)..")
                .should().beFreeOfCycles()
                .because("IAM feature subpackages must not depend on each other cyclically");
        ArchRule securityDoesNotDependOnPersistence = noClasses()
                .that().resideInAPackage("io.roseiot.shared.security..")
                .should().dependOnClassesThat().resideInAPackage("io.roseiot.shared.mybatis..")
                .because("security identity contracts must not depend on MyBatis infrastructure");
        ArchRule controllersDoNotDependOnMappers = noClasses()
                .that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().areAssignableTo(BaseMapper.class)
                .because("HTTP controllers call feature services instead of persistence mappers");
        ArchRule controllersDoNotDependOnPersistenceEntities = noClasses()
                .that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().areAnnotatedWith(TableName.class)
                .because("HTTP controllers use API models instead of MyBatis persistence entities");
        ArchRule persistenceEntitiesArePackagePrivate = classes()
                .that().areAnnotatedWith(TableName.class)
                .should().notBePublic()
                .because("MyBatis persistence entities remain internal to their feature package");
        ArchRule mapperInterfacesArePackagePrivate = classes()
                .that().areAssignableTo(BaseMapper.class)
                .should().notBePublic()
                .because("Mapper interfaces remain internal to their feature package");
        ArchRule transactionsBelongToServices = methods()
                .that().areAnnotatedWith(Transactional.class)
                .should().beDeclaredInClassesThat().areAnnotatedWith(Service.class)
                .because("transaction boundaries belong to application services");
        ArchRule servicesHaveTransactionalBoundaries = classes()
                .that().areAnnotatedWith(Service.class)
                .should().beAnnotatedWith(Transactional.class)
                .because("services own the transaction boundaries");
        ArchRule noFieldInjection = noFields().should().beAnnotatedWith(Autowired.class)
                .because("Spring dependencies use constructor injection");
        ArchRule configurationClassesUseConfigSuffix = classes()
                .that().areAnnotatedWith(Configuration.class)
                .should().haveSimpleNameEndingWith("Config")
                .because("configuration classes follow the project's Spring naming convention");

        noFeatureCycles.check(applicationClasses);
        noIamFeatureCycles.check(applicationClasses);
        securityDoesNotDependOnPersistence.check(applicationClasses);
        controllersDoNotDependOnMappers.check(applicationClasses);
        controllersDoNotDependOnPersistenceEntities.check(applicationClasses);
        persistenceEntitiesArePackagePrivate.check(applicationClasses);
        mapperInterfacesArePackagePrivate.check(applicationClasses);
        transactionsBelongToServices.check(applicationClasses);
        servicesHaveTransactionalBoundaries.check(applicationClasses);
        var services = applicationClasses.stream().filter(type -> type.isAnnotatedWith(Service.class)).toList();
        assertThat(services).allSatisfy(service ->
                assertThat(service.getAnnotationOfType(Transactional.class).readOnly()).isTrue());
        noFieldInjection.check(applicationClasses);
        configurationClassesUseConfigSuffix.check(applicationClasses);
    }
}
