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

public class SlotApiTest {

    @BeforeClass(alwaysRun = true)
    public void setup() {
        RestAssured.baseURI = TestConfig.getBaseUrl();
    }

    @Test(groups = {"smoke", "api"})
    public void testCreateSlotApi() {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("title", "REST Assured Test Slot");
        requestBody.put("capacity", 5);

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/api/slots")
        .then()
            .statusCode(201)
            .body("success", equalTo(true))
            .body("data.title", equalTo("REST Assured Test Slot"))
            .body("data.capacity", equalTo(5))
            .body("data.reservedCount", equalTo(0));
    }

    @Test(groups = {"regression", "api"})
    public void testGetAllSlotsApi() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/slots")
        .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("data", isA(java.util.List.class));
    }

    @Test(groups = {"regression", "api"})
    public void testGetSlotInvariantStatusApi() {
        // First create a slot
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("title", "Invariant Test Slot API");
        requestBody.put("capacity", 2);

        int slotId = given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .post("/api/slots")
            .then()
            .statusCode(201)
            .extract()
            .path("data.id");

        // Fetch invariant status
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/slots/" + slotId + "/invariant")
        .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("data.slotId", equalTo(slotId))
            .body("data.capacity", equalTo(2))
            .body("data.doubleBooked", equalTo(false))
            .body("data.status", equalTo("INVARIANT_HOLDING"));
    }
}
