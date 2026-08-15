package com.slotguard.application;

import org.h2.tools.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.sql.SQLException;

@SpringBootApplication
public class SlotGuardApplication {

    @Value("${h2.tcp.port:9092}")
    private String h2TcpPort;

    public static void main(String[] args) {
        SpringApplication.run(SlotGuardApplication.class, args);
    }

    /**
     * Expose H2 in-memory database via TCP so that the automation test runner
     * (running in a separate JVM) can connect directly for database invariant assertions.
     * Connect via: jdbc:h2:tcp://localhost:9092/mem:slotguarddb
     *
     * Only starts when h2.tcp.enabled=true (default: true for the application, disabled in unit tests).
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(name = "h2.tcp.enabled", havingValue = "true", matchIfMissing = true)
    public Server h2TcpServer() throws SQLException {
        return Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", h2TcpPort);
    }
}

