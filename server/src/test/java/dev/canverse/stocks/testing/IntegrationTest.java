package dev.canverse.stocks.testing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.testcontainers.junit.jupiter.Testcontainers;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ContextConfiguration(initializers = SuitePostgresContextInitializer.class)
@Import(IntegrationTestConfiguration.class)
@TestExecutionListeners(listeners = IntegrationTestExecutionListener.class, mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
// This annotation only keeps Testcontainers' disabledWithoutDocker guard; SuitePostgres owns the container lifecycle.
@Testcontainers(disabledWithoutDocker = true)
public @interface IntegrationTest {

    @AliasFor(annotation = SpringBootTest.class, attribute = "webEnvironment")
    WebEnvironment webEnvironment() default WebEnvironment.MOCK;

    @AliasFor(annotation = SpringBootTest.class, attribute = "properties")
    String[] properties() default {};
}
