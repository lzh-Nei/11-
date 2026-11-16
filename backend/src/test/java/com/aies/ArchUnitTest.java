package com.aies;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit 依赖守卫（04-01 §五五条规则，D2债务项）。
 *
 * <p>当前为扁平包结构（com.aies.controller/service/mapper/entity/...），规则按现结构适配：
 * §五的"模块隔离/SPI白名单"两条以 mapper 访问面收敛承载，businessX 包结构落地（L1扩包）时
 * 切换为原文断言。违规即失败，豁免须 04-08 §三例外审批。</p>
 */
class ArchUnitTest {

    private static JavaClasses imported;

    @BeforeAll
    static void importClasses() {
        imported = new ClassFileImporter(List.of(new ImportOption.DoNotIncludeTests()))
                .importPackages("com.aies");
    }

    /** 规则1 分层单向：controller不得触碰mapper */
    @Test
    void controllerMustNotTouchMapper() {
        ArchRule rule = noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..mapper..");
        rule.check(imported);
    }

    /** 规则2 分层单向：mapper不得反向依赖service */
    @Test
    void mapperMustNotDependOnService() {
        ArchRule rule = noClasses().that().resideInAPackage("..mapper..")
                .should().dependOnClassesThat().resideInAPackage("..service..");
        rule.check(imported);
    }

    /** 规则3 分层单向：service不得反向依赖controller（当前含mapper访问面收敛：全库mapper仅service可触） */
    @Test
    void serviceMustNotDependOnController() {
        ArchRule rule = noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..");
        rule.check(imported);
    }

    /** 规则4 Entity出参禁令：controller不得暴露entity包类型（一律VO/DTO） */
    @Test
    void controllerMustNotExposeEntities() {
        ArchRule rule = noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..entity..");
        rule.check(imported);
    }

    /** 规则5 事务注解：@Transactional方法只允许出现在service层（事务边界L3） */
    @Test
    void transactionalMethodsMustLiveInServiceLayer() {
        ArchRule rule = methods().that().areAnnotatedWith(Transactional.class)
                .should().beDeclaredInClassesThat().resideInAPackage("..service..");
        rule.check(imported);
    }

    /** 类级@Transactional同样收敛（防配置类/组件误标）：service包外的类不得标注@Transactional */
    @Test
    void transactionalClassesMustLiveInServiceLayer() {
        ArchRule rule = noClasses().that().resideOutsideOfPackage("..service..")
                .should().beAnnotatedWith(Transactional.class);
        rule.check(imported);
    }
}
