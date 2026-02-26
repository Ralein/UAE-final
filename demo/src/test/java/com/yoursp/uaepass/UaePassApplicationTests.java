package com.yoursp.uaepass;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Requires Redis + PostgreSQL — run in integration test profile only")
class UaePassApplicationTests {

    @Test
    void contextLoads() {
    }
}
