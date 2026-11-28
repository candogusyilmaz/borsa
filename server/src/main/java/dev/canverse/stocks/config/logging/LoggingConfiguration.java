package dev.canverse.stocks.config.logging;

import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;

@Configuration(proxyBeanMethods = false)
public class LoggingConfiguration {

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setName("requestLoggingFilter");
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
