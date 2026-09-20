package dev.canverse.stocks.testing;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

public final class IntegrationTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        var beans = testContext.getApplicationContext();
        beans.getBeanProvider(TestClock.class).ifAvailable(TestClock::reset);
        beans.getBeanProvider(RecordingIdGenerator.class).ifAvailable(RecordingIdGenerator::reset);
    }
}
