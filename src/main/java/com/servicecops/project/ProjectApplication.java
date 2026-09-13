package com.servicecops.project;

import com.google.cloud.spring.autoconfigure.secretmanager.GcpSecretManagerAutoConfiguration;
import com.servicecops.project.models.jpahelpers.repository.JetRepositoryImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaRepositories(repositoryBaseClass = JetRepositoryImpl.class)
@EnableScheduling
@SpringBootApplication(exclude = GcpSecretManagerAutoConfiguration.class)
public class ProjectApplication {
	public static void main(String[] args) {
		SpringApplication.run(ProjectApplication.class, args);
	}
}
