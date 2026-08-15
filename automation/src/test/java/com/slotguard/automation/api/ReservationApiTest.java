package com.slotguard.automation.api;

import com.slotguard.automation.config.TestConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ReservationApiTest {

    @BeforeClass(alwaysRun = true)
    public void setup() {
        RestAssured.baseURI = TestConfig.getBaseUrl();
    }

    @Test(groups = {"smoke", "api"})
    public void testMakeReservationApiSuccess() {
        // Create slot with capacity 1
        Map<String, Object> slotReq = new HashMap<>();
        slotReq.put("title", "Single Reservation Slot");
        slotReq.put("capacity", 1);

        int slotId = given()
            .contentType(ContentType.JSON)
            .body(slotReq)
            .post("/api/slots")
            .then()
            .statusCode(201)
            .extract()
            .path("data.id");

        // Make reservation
        Map<String, Object> resReq = new HashMap<>();
        resReq.put("slotId", slotId);
        resReq.put("userName", "API_User_1");

        given()
            .contentType(ContentType.JSON)
            .body(resReq)
        .when()
            .post("/api/reservations")
        .then()
            .statusCode(201)
            .body("success", equalTo(true))
            .body("data.slotId", equalTo(slotId))
            .body("data.userName", equalTo("API_User_1"));
    }

    @Test(groups = {"regression", "api"})
    public void testMakeReservationApiExceedCapacity() {
        // Create slot with capacity 1
        Map<String, Object> slotReq = new HashMap<>();
        slotReq.put("title", "Capacity Limit Test Slot");
        slotReq.put("capacity", 1);

        int slotId = given()
            .contentType(ContentType.JSON)
            .body(slotReq)
            .post("/api/slots")
            .then()
            .statusCode(201)
            .extract()
            .path("data.id");

        // First reservation should succeed
        Map<String, Object> resReq1 = new HashMap<>();
        resReq1.put("slotId", slotId);
        resReq1.put("userName", "First_User");

        given()
            .contentType(ContentType.JSON)
            .body(resReq1)
            .post("/api/reservations")
            .then()
            .statusCode(201);

        // Second reservation should be rejected with HTTP 409
        Map<String, Object> resReq2 = new HashMap<>();
        resReq2.put("slotId", slotId);
        resReq2.put("userName", "Second_User");

        given()
            .contentType(ContentType.JSON)
            .body(resReq2)
        .when()
            .post("/api/reservations")
        .then()
            .statusCode(409)
            .body("success", equalTo(false))
            .body("message", containsString("capacity exceeded"));
    }
}
