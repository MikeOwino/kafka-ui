package com.provectus.kafka.ui.base;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.WebDriverRunner;
import com.codeborne.selenide.logevents.SelenideLogger;
import com.provectus.kafka.ui.helpers.Helpers;
import com.provectus.kafka.ui.pages.Pages;
import com.provectus.kafka.ui.screenshots.Screenshooter;
import com.provectus.kafka.ui.steps.Steps;
import io.github.cdimascio.dotenv.Dotenv;
import io.qameta.allure.Allure;
import io.qameta.allure.selenide.AllureSelenide;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@Slf4j
@DisplayNameGeneration(CamelCaseToSpacedDisplayNameGenerator.class)
public class BaseTest {

    protected Steps steps = Steps.INSTANCE;
    protected Pages pages = Pages.INSTANCE;
    protected Helpers helpers = Helpers.INSTANCE;

    private Screenshooter screenshooter = new Screenshooter();

    private static final String IMAGE_NAME = TestConfiguration.IMAGE_NAME;
    private static final String IMAGE_TAG = TestConfiguration.IMAGE_TAG;
    protected static BrowserWebDriverContainer<?> webDriverContainer = null;

    public void compareScreenshots(String name) {
        screenshooter.compareScreenshots(name);
    }

    public void compareScreenshots(String name, Boolean shouldUpdateScreenshots) {
        screenshooter.compareScreenshots(name, shouldUpdateScreenshots);
    }

    @BeforeEach
    public void setWebDriver(){
        RemoteWebDriver remoteWebDriver = webDriverContainer.getWebDriver();
        WebDriverRunner.setWebDriver(remoteWebDriver);
        remoteWebDriver.manage().window().setSize(new Dimension(1440, 1024));
    }

    @BeforeAll
    public static void start() {
        DockerImageName image = DockerImageName.parse(IMAGE_NAME).withTag(IMAGE_TAG);
        webDriverContainer = new BrowserWebDriverContainer<>(image)
                .withCapabilities(new ChromeOptions().addArguments("--disable-dev-shm-usage"))
                .waitingFor(Wait.forHttp("/"))
                .waitingFor(Wait.forLogMessage(".*Started Selenium Standalone.*", 1));
        Testcontainers.exposeHostPorts(8080);
        webDriverContainer.start();
        webDriverContainer.isRunning();
        webDriverContainer.isHostAccessible();
    }

    @AfterAll
    public static void tearDown(){
        if(webDriverContainer.isRunning()) {
            webDriverContainer.close();
            webDriverContainer.stop();
        }
    }

    static {
        if (!new File("./.env").exists()) {
            try {
                FileUtils.copyFile(new File(".env.example"), new File(".env"));
            } catch (IOException e) {
                log.error("couldn't copy .env.example to .env. Please add .env");
                e.printStackTrace();
            }
        }
        Dotenv.load().entries().forEach(env -> System.setProperty(env.getKey(), env.getValue()));
        if (TestConfiguration.CLEAR_REPORTS_DIR) {
            clearReports();
        }
        setup();
    }

    @AfterEach
    public void afterMethod() {
        Allure.addAttachment("Screenshot",
                new ByteArrayInputStream(((TakesScreenshot) webDriverContainer.getWebDriver()).getScreenshotAs(OutputType.BYTES)));
    }

    @SneakyThrows
    private static void setup() {
        Configuration.reportsFolder = TestConfiguration.REPORTS_FOLDER;
        Configuration.screenshots = TestConfiguration.SCREENSHOTS;
        Configuration.savePageSource = TestConfiguration.SAVE_PAGE_SOURCE;
        Configuration.reopenBrowserOnFail = TestConfiguration.REOPEN_BROWSER_ON_FAIL;
        Configuration.browser = TestConfiguration.BROWSER;
        Configuration.baseUrl = TestConfiguration.BASE_WEB_URL;
        Configuration.timeout = 10000;
        Configuration.browserSize = TestConfiguration.BROWSER_SIZE;
        SelenideLogger.addListener("allure", new AllureSelenide().savePageSource(false));
    }

    public static void clearReports() {
        log.info("Clearing reports dir [%s]...".formatted(TestConfiguration.REPORTS_FOLDER));
        File allureResults = new File(TestConfiguration.REPORTS_FOLDER);
        if (allureResults.isDirectory()) {
            File[] list = allureResults.listFiles();
            if (list != null) {
                Arrays.stream(list)
                        .sequential()
                        .filter(e -> !e.getName().equals("categories.json"))
                        .forEach(File::delete);
            }
        }
    }
}