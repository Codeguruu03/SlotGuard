package com.slotguard.automation.ui;

import com.slotguard.automation.config.TestConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.Duration;

public class SeleniumReservationTest {

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeClass(alwaysRun = true)
    public void setup() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @Test(groups = {"ui", "smoke"})
    public void testUiDashboardHeaderAndTitle() {
        driver.get(TestConfig.getBaseUrl());
        
        WebElement headerTitle = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".logo-title h1")));
        Assert.assertEquals(headerTitle.getText(), "SlotGuard");

        WebElement modeBadge = driver.findElement(By.id("modeBadge"));
        Assert.assertTrue(modeBadge.isDisplayed(), "Mode badge must be visible");
    }

    @Test(groups = {"ui", "regression"})
    public void testCreateSlotAndReserveUi() {
        driver.get(TestConfig.getBaseUrl());

        // Create new slot
        WebElement titleInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("slotTitleInput")));
        titleInput.sendKeys("Selenium Automated Slot");

        WebElement capacityInput = driver.findElement(By.id("slotCapacityInput"));
        capacityInput.clear();
        capacityInput.sendKeys("2");

        WebElement submitBtn = driver.findElement(By.cssSelector("form button[type='submit']"));
        submitBtn.click();

        // Verify log console entry
        WebElement logConsole = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("logConsole")));
        wait.until(ExpectedConditions.textToBePresentInElement(logConsole, "Selenium Automated Slot"));

        // Verify slot card appears in slots container
        WebElement slotsContainer = driver.findElement(By.id("slotsContainer"));
        Assert.assertTrue(slotsContainer.getText().contains("Selenium Automated Slot"), "Created slot should appear in slots container");
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
