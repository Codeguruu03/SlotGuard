package com.slotguard.automation.config;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class TestConfig {

    private static final Properties properties = new Properties();

    static {
        try (InputStream is = TestConfig.class.getClassLoader().getResourceAsStream("test-config.properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (Exception ignored) {}
    }

    public static String getBaseUrl() {
        return System.getProperty("baseUrl", properties.getProperty("baseUrl", "http://localhost:8080"));
    }

    public static String getDbUrl() {
        return System.getProperty("dbUrl", properties.getProperty("dbUrl", "jdbc:h2:mem:slotguarddb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"));
    }

    public static String getDbUser() {
        return System.getProperty("dbUser", properties.getProperty("dbUser", "sa"));
    }

    public static String getDbPassword() {
        return System.getProperty("dbPassword", properties.getProperty("dbPassword", ""));
    }

    public static Connection getDatabaseConnection() throws Exception {
        return DriverManager.getConnection(getDbUrl(), getDbUser(), getDbPassword());
    }

    public static boolean isAppRunning() {
        try {
            URL url = new URL(getBaseUrl() + "/api/slots");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            int code = connection.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
