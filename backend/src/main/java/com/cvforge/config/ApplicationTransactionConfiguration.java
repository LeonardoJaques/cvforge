package com.cvforge.config;

import com.cvforge.application.service.JobComparisonApplicationService;
import com.cvforge.application.service.ResumeApplicationService;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/** Infrastructure-owned transaction boundaries for persistence-backed use cases. */
@Configuration
@EnableTransactionManagement
public class ApplicationTransactionConfiguration {
    @Bean
    DefaultPointcutAdvisor applicationServiceTransactions(PlatformTransactionManager transactionManager) {
        RuleBasedTransactionAttribute write = new RuleBasedTransactionAttribute();
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        RuleBasedTransactionAttribute readOnly = new RuleBasedTransactionAttribute();
        readOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        readOnly.setReadOnly(true);

        NameMatchTransactionAttributeSource attributes = new NameMatchTransactionAttributeSource();
        attributes.setDefaultTransactionAttribute(write);
        attributes.addTransactionalMethod("get*", readOnly);
        attributes.addTransactionalMethod("search*", readOnly);
        attributes.addTransactionalMethod("count*", readOnly);
        attributes.addTransactionalMethod("list*", readOnly);

        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setClassFilter(type -> type == ResumeApplicationService.class
                || type == JobComparisonApplicationService.class);
        pointcut.setMappedNames("*");

        return new DefaultPointcutAdvisor(pointcut,
                new TransactionInterceptor(transactionManager, attributes));
    }
}
