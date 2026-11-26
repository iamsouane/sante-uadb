package sn.uadb.supervision.superviseur;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SupervisionProperties.class)
public class SupervisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupervisionApplication.class, args);
    }
}
