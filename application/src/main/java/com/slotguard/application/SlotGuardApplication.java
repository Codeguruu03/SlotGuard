package com.slotguard.application;

import org.h2.tools.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.sql.SQLException;

@SpringBootApplication
public class SlotGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlotGuardApplication.class, args);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public Server h2TcpServer() throws SQLException {
        return Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", "9092");
    }
}
