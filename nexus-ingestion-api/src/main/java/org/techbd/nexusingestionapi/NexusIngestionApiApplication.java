package org.techbd.nexusingestionapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.techbd.config.AppConfig;

@SpringBootApplication(scanBasePackages = { "org.techbd" })
@EnableConfigurationProperties(AppConfig.class)
public class NexusIngestionApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(NexusIngestionApiApplication.class, args);
	}

}
