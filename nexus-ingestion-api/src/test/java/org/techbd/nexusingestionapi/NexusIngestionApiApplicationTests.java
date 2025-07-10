package org.techbd.nexusingestionapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.techbd.config.AppConfig;

@SpringBootTest
@EnableConfigurationProperties(AppConfig.class)
class NexusIngestionApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
