package com.servicecops.project;

import com.google.cloud.spring.autoconfigure.secretmanager.GcpSecretManagerAutoConfiguration;
import com.servicecops.project.models.jpahelpers.repository.JetRepositoryImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaRepositories(repositoryBaseClass = JetRepositoryImpl.class)
@SpringBootApplication(exclude = GcpSecretManagerAutoConfiguration.class)
public class ProjectApplication {
	public static void main(String[] args) {
		SpringApplication.run(ProjectApplication.class, args);
	}
}
