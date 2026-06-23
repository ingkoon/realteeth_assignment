package io.github.ingkoon.realteeth_assignment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RealteethAssignmentApplicationTests {

    @Test
    void contextLoads() {
    }

}
