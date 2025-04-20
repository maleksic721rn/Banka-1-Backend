package com.banka1.banking.cucumber;

import com.banka1.testing.config.JwtTestConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "postgres"})
@Import(JwtTestConfiguration.class)
public class CucumberSpringConfiguration {
}
