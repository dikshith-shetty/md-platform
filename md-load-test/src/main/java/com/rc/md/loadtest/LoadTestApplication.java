package com.rc.md.loadtest;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = "com.rc.md.client.analytics")
public class LoadTestApplication implements CommandLineRunner {

    private final LoadTestRunner runner;

    public LoadTestApplication(LoadTestRunner runner) {
        this.runner = runner;
    }

    public static void main(String[] args) {
        SpringApplication.run(LoadTestApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        runner.runOnce();
        // exit after test so it behaves like a CLI
        System.exit(0);
    }
}
