package com.discord.bot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"discord.token=test-token-not-real",
		"spring.autoconfigure.exclude=com.discord.bot.config.JdaConfig"
})
class BotApplicationTests {

	@Test
	void contextLoads() {
	}

}
