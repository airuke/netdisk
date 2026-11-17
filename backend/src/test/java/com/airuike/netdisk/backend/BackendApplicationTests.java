package com.airuike.netdisk.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("files-test")
@TestPropertySource(properties={
    "spring.datasource.url=jdbc:h2:mem:files_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password="
})
@Import(MemoryStorageConfig.class)
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
