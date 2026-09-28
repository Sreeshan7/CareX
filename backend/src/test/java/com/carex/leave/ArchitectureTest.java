package com.carex.leave;

import com.carex.leave.leave.balance.BalanceService;
import com.carex.leave.leave.request.LeaveRequest;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

import javax.sql.DataSource;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Structural guarantees behind "AI cannot modify the database / bypass the workflow" (implementation.md §18.1).
 */
class ArchitectureTest {
    static JavaClasses classes;

    @BeforeAll
    static void load() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.carex.leave");
    }

    @Test
    void assistantCannotReachTheWorkflowWriteSide() {
        noClasses().that().resideInAPackage("..assistant..")
                .should().dependOnClassesThat().resideInAPackage("com.carex.leave.workflow")
                .because("the assistant may only produce proposals; mutations go through the REST endpoints")
                .check(classes);
    }

    @Test
    void assistantCannotTouchRepositoriesOrJdbc() {
        noClasses().that().resideInAPackage("..assistant..")
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .orShould().dependOnClassesThat().areAssignableTo(DataSource.class)
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManager")
                .because("AI must never directly modify PostgreSQL")
                .check(classes);
    }

    @Test
    void assistantCannotCallBalanceMutators() {
        noClasses().that().resideInAPackage("..assistant..")
                .should().callMethodWhere(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                        com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner(
                                com.tngtech.archunit.base.DescribedPredicate.describe("BalanceService",
                                        o -> o.isEquivalentTo(BalanceService.class))))
                        .and(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching(
                                        "reserve|releasePending|commitPending|restoreUsed|lockOrCreate|ensureRows|balancesFor"))))
                .check(classes);
    }

    @Test
    void onlyTheWorkflowServiceMutatesLeaveRequestState() {
        noClasses().that().resideOutsideOfPackage("com.carex.leave.workflow")
                .should().callMethodWhere(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                        com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner(
                                com.tngtech.archunit.base.DescribedPredicate.describe("LeaveRequest",
                                        o -> o.isEquivalentTo(LeaveRequest.class))))
                        .and(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching("transitionTo|set.*"))))
                .because("only LeaveWorkflowService may change status/decision fields")
                .check(classes);
    }

    @Test
    void balanceMutatorsOnlyFromWorkflow() {
        noClasses().that().resideOutsideOfPackages("com.carex.leave.workflow", "com.carex.leave.leave.balance")
                .should().callMethodWhere(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                        com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner(
                                com.tngtech.archunit.base.DescribedPredicate.describe("BalanceService",
                                        o -> o.isEquivalentTo(BalanceService.class))))
                        .and(com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching(
                                        "reserve|releasePending|commitPending|restoreUsed|lockOrCreate"))))
                .check(classes);
    }
}
