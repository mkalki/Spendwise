package com.spendwise;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpendwiseApplication {

    public static void main(String[] args) {
        // PostgreSQL may reject JVM timezone aliases such as Asia/Calcutta during JDBC startup.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(SpendwiseApplication.class, args);
    }
}
