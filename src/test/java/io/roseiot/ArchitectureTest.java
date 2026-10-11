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
        ArchRule noFieldInjection = noFields().should().beAnnotatedWith(Autowired.class)
                .because("Spring dependencies use constructor injection");
        ArchRule configurationClassesUseConfigSuffix = classes()
                .that().areAnnotatedWith(Configuration.class)
                .should().haveSimpleNameEndingWith("Config")
                .because("configuration classes follow the project's Spring naming convention");

        noFeatureCycles.check(applicationClasses);
        noIamFeatureCycles.check(applicationClasses);
        controllersDoNotDependOnMappers.check(applicationClasses);
        controllersDoNotDependOnPersistenceEntities.check(applicationClasses);
        persistenceEntitiesArePackagePrivate.check(applicationClasses);
        mapperInterfacesArePackagePrivate.check(applicationClasses);
        transactionsBelongToServices.check(applicationClasses);
        noFieldInjection.check(applicationClasses);
        configurationClassesUseConfigSuffix.check(applicationClasses);
    }
}
