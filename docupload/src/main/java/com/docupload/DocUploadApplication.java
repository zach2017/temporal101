package com.docupload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DocUploadApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocUploadApplication.class, args);
    }
}
