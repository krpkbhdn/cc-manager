package com.github.krpkbhdn.ccmanager;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Desktop app: Spring runs in-process without a web server, the Compose UI is started by the caller. */
@SpringBootApplication
public class CcManagerApplication {

    public static ConfigurableApplicationContext start(String[] args) {
        return new SpringApplicationBuilder(CcManagerApplication.class)
                .web(WebApplicationType.NONE)
                .headless(false)
                .run(args);
    }
}
