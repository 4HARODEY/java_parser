package org.cyberslavs.parser.service;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.cyberslavs.parser.entity.Tender;
import org.cyberslavs.parser.repo.TenderRepository;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.FileWriter;
import java.io.IOException;

import java.time.Duration;
import java.util.*;

public class Parser {

    WebDriver driver;
    ChromeOptions webOptions;
    @Autowired
    TenderRepository tenderRepository;
    public Parser(){
        System.setProperty("webdriver.chrome.driver", "C:\\Program Files\\chromedriver-win64\\chromedriver-win64\\chromedriver.exe");
        this.webOptions = new ChromeOptions();
        this.webOptions.addArguments("--headless");
        this.webOptions.addArguments("--ignore-certificate-errors");
        this.driver = new ChromeDriver(this.webOptions);
    }
    public List<Tender> parse() throws InterruptedException {
        WebDriverManager.chromedriver().setup();

        this.driver.get("https://etp.tatneft.ru/pls/tzp/f?p=220:562:11281430464650::::P562_OPEN_MODE,GLB_NAV_ROOT_ID,GLB_NAV_ID:,12920020,12920020");
        List<Tender> tenders = new ArrayList<>();
        List<String> option_list = Arrays.asList("Предложение", "Тендер", "Завершенные", "Материалы");

        WebDriverWait wait = new WebDriverWait(this.driver, Duration.ofSeconds(5));
        wait.pollingEvery(Duration.ofMillis(250));
        for(int i = 0; i < 3; i++) {
            List<WebElement> selectors_panel = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("select.selectlist"))).subList(0, 3);
            selectors_panel.get(i).click();
            try {
                List<WebElement> options = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.tagName("option")));
                for(WebElement elem: options) {
                    if(elem.getText().equals(option_list.get(i))) {
                        elem.click();
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println("Ошибка при взаимодействии с элементом: " + e.getMessage());
            }
        };

        WebElement table = this.driver.findElement(By.cssSelector("table.a-IRR-table")).findElement(By.tagName("tbody"));
        List<WebElement> target_list = table.findElements(By.tagName("tr"));
        List<HashMap> json_list = new ArrayList<>();
        List<String> head_name = new ArrayList<>();

        for(WebElement elem: target_list.get(0).findElements(By.className("a-IRR-headerLabel"))) {
            head_name.add(elem.getText());
        };

        for( WebElement elem: target_list.subList(1, target_list.size()) ) {
            List<WebElement> into_list = elem.findElements(By.tagName("td"));
            HashMap<String, Object> map = new HashMap<>();

            for(int i = 1; i < into_list.size(); i++) {
                WebElement web_elem = into_list.get(i);
                String key = head_name.get(i).replace("\n", " ");
                String val = extractTextFromElement(web_elem);
                map.put(key, val);
            };

            try {
                WebElement clicked_info = elem.findElement(By.cssSelector("td[headers='NAME_LINK']"));
                if( !clicked_info.findElements( By.tagName("a") ).isEmpty() ) {
                    HashMap<String, HashMap> dop_hash_map_inf = new HashMap<>();

                    WebDriver into_driver = new ChromeDriver(this.webOptions);
                    into_driver.get(clicked_info.findElement( By.tagName("a") ).getAttribute("href"));
                    WebElement dop_info_table = into_driver.findElement(By.className("ReportTbl"));

                    List<WebElement> data_blocks = dop_info_table.findElements(By.tagName("tbody"));

                    List<String> pre_table_heads = new ArrayList<>();

                    for(WebElement pre_elem: data_blocks.get(0).findElements(By.tagName("th"))) {
                        pre_table_heads.add(pre_elem.getText());
                    };

                    for(WebElement tr: data_blocks.get(1).findElements(By.tagName("tr"))) {
                        List<WebElement> tds = tr.findElements(By.tagName("td"));
                        HashMap<String, String> tr_hash_map = new HashMap<>();
                        for(int j = 1; j < tds.size(); j++) {
                            tr_hash_map.put(pre_table_heads.get(j).replace("\n", " "), tds.get(j).getText());
                        };
                        dop_hash_map_inf.put(tds.get(0).getText(), tr_hash_map);
                    };
                    map.put("Информация из вложенной таблицы", dop_hash_map_inf);
                    into_driver.quit();
                } else {
                    map.put("Информация из вложенной таблицы", null);
                };
            } catch (Throwable e) {
                e.printStackTrace();
            }
            tenders.add(new Tender((String) map.get(head_name.get(2)), (String) map.get(head_name.get(1)), "", (String) map.get(head_name.get(8)), (String) map.get(head_name.get(7)), (String) map.get(head_name.get(5))));
            json_list.add(map);
        };
        this.driver.quit();

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(json_list);

        try (FileWriter file = new FileWriter("./data_tatneft.json")) {
            file.write(json);
            System.out.println("Successfully Copied JSON Object to File...");
            System.out.println("\nJSON Object: " + json);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //System.out.println(json);
        return tenders;
    };

    private static String extractTextFromElement(WebElement element) {
        List<WebElement> children = element.findElements(By.xpath("./*"));

        if (children.isEmpty()) {
            return element.getText();
        } else {
            StringBuilder text = new StringBuilder();
            for (WebElement child : children) {
                text.append(extractTextFromElement(child));
            }
            return text.toString();
        }
    }
}