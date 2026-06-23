package io.github.ingkoon.realteeth_assignment;

import org.springframework.boot.SpringApplication;

public class TestRealteethAssignmentApplication {

    public static void main(String[] args) {
        SpringApplication.from(RealteethAssignmentApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
