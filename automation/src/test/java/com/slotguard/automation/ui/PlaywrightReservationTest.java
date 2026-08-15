package com.slotguard.automation.ui;

import com.microsoft.playwright.*;
import com.slotguard.automation.config.TestConfig;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class PlaywrightReservationTest {

    private Playwright playwright;

    @BeforeClass(alwaysRun = true)
    public void setup() {
        playwright = Playwright.create();
    }

    @DataProvider(name = "browserEngineProvider")
    public Object[][] browserEngineProvider() {
        return new Object[][]{
                {"chromium"},
                {"firefox"},
                {"webkit"}
        };
    }

    @Test(dataProvider = "browserEngineProvider", groups = {"cross-browser", "ui"})
    public void testCrossBrowserReservationWorkflow(String browserEngine) {
        Browser browser;
        switch (browserEngine.toLowerCase()) {
            case "firefox":
                browser = playwright.firefox().launch(new BrowserType.LaunchOptions().setHeadless(true));
                break;
            case "webkit":
                browser = playwright.webkit().launch(new BrowserType.LaunchOptions().setHeadless(true));
                break;
            case "chromium":
            default:
                browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                break;
        }

        BrowserContext context = browser.newContext();
        Page page = context.newPage();

        page.navigate(TestConfig.getBaseUrl());

        // Assert Title
        String title = page.textContent(".logo-title h1");
        Assert.assertEquals(title, "SlotGuard", "Title match failed on browser engine: " + browserEngine);

        // Fill form and create slot
        page.fill("#slotTitleInput", "Playwright " + browserEngine + " Slot");
        page.fill("#slotCapacityInput", "3");
        page.click("form button[type='submit']");

        // Verify slot card created
        page.waitForSelector("#slotsContainer:has-text('Playwright " + browserEngine + " Slot')");
        String slotsText = page.textContent("#slotsContainer");
        Assert.assertTrue(slotsText.contains("Playwright " + browserEngine + " Slot"),
                "Slot creation failed on browser engine: " + browserEngine);

        context.close();
        browser.close();
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (playwright != null) {
            playwright.close();
        }
    }
}
