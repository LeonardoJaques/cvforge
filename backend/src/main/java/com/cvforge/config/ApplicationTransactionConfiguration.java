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
        write.setReadOnly(false);

        RuleBasedTransactionAttribute readOnly = new RuleBasedTransactionAttribute();
        readOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        readOnly.setReadOnly(true);

        NameMatchTransactionAttributeSource attributes = new NameMatchTransactionAttributeSource();
        attributes.addTransactionalMethod("getAllResumes", readOnly);
        attributes.addTransactionalMethod("getResumeById", readOnly);
        attributes.addTransactionalMethod("getResumesByLayout", readOnly);
        attributes.addTransactionalMethod("searchByTitle", readOnly);
        attributes.addTransactionalMethod("countByUserId", readOnly);
        attributes.addTransactionalMethod("list", readOnly);
        attributes.addTransactionalMethod("createResume", write);
        attributes.addTransactionalMethod("updateResume", write);
        attributes.addTransactionalMethod("deleteResume", write);
        attributes.addTransactionalMethod("save", write);
        attributes.addTransactionalMethod("delete", write);

        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setClassFilter(type -> type == ResumeApplicationService.class
                || type == JobComparisonApplicationService.class);
        pointcut.setMappedNames("*");

        return new DefaultPointcutAdvisor(pointcut,
                new TransactionInterceptor(transactionManager, attributes));
    }
}
