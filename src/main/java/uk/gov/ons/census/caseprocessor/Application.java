package uk.gov.ons.census.caseprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.integration.annotation.IntegrationComponentScan;

@SpringBootApplication
@IntegrationComponentScan
@EntityScan("uk.gov.ons.census.common.model.entity")
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class);
  }
}
