package com.datahyper.crawler;

import com.google.common.base.Joiner;
import com.ruiyun.jvppeteer.core.Puppeteer;
import com.ruiyun.jvppeteer.core.browser.Browser;
import com.ruiyun.jvppeteer.core.page.Page;
import com.ruiyun.jvppeteer.options.LaunchOptions;
import com.ruiyun.jvppeteer.options.LaunchOptionsBuilder;
import com.ruiyun.jvppeteer.options.PageNavigateOptions;
import com.ruiyun.jvppeteer.options.Viewport;
import org.apache.commons.collections4.ListUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 文书网爬虫Demo
 *
 * @author dataHyper
 * @date 2022-04-27 17:10
 */
public class CrawlerMain {

    private static PageNavigateOptions pageNavigateOptions;

    private static LaunchOptions options;

    static {
        pageNavigateOptions = new PageNavigateOptions();
        pageNavigateOptions.setWaitUntil(Collections.singletonList("networkidle0"));
        Viewport viewport = new Viewport();
        viewport.setHeight(1080);
        viewport.setWidth(1920);
        options = new LaunchOptionsBuilder()
                .withArgs(Arrays.asList("--no-sandbox", "--disable-setuid-sandbox", "--passive-listeners-default",
                        "--disable-gpu"))
                // 这里换成自己的chrome数据存储路径，这样登录一次后，就不需要每次都登录
//                .withUserDataDir("C:\\Users\\XXX\\AppData\\Local\\Google\\Chrome\\User Data\\Default\\")
                .withHeadless(false)
                .withIgnoreDefaultArgs(Arrays.asList("--enable-automation"))
                .withExecutablePath("D:\\Program Files\\谷歌76\\chrome.exe").build();
        options.setViewport(viewport);
    }

    public static void main(String[] args) throws Exception {
        // 需要采集的关键词
        String[] kws = {"群体性", "聚众", "非法爬取", "网络"};
        Browser browser = Puppeteer.launch(options);
        for (String kw : kws) {
            // 结束时间
            String endTime = "2022-04-27";
//            String endTime = "2022-04-30";
            // 开始时间
            String startTime = "2005-01-01";
//            while (true) {
            //TODO 这里直接将时间写死，官网最多只能返回600页面，如果需要全量数据，把这里注释放开，并按照自己的需求按时间区间遍历所有的数据即可
            String nextTime = startTime;
//            String nextTime =
//                    DateUtils.formatDate(DateUtils.getTimestamp(endTime, "yyyy-MM-dd") * 1000 - (518400200 * 5L),
//                            "yyyy-MM-dd");
            System.out.println("kw:" + kw + ", " + nextTime + "-->" + endTime);
            int size = 600;
            try {
                Page page = browser.newPage();
                //FIXME 建议在这里打上断点，如果没有登录先手动登录后，再放行断点
                page.goTo("https://wenshu.court.gov.cn/");
                page.click("div.advenced-search");

                page.type("#qbValue", kw, 100);
                page.type("#cprqStart", nextTime, 100);
                page.type("#cprqEnd", endTime, 100);
                page.click("a[id=searchBtn]");

//                endTime = nextTime;
//                if (DateUtils.getTimestamp(startTime, "yyyy-MM-dd") > DateUtils.getTimestamp(endTime, "yyyy-MM" +
//                        "-dd")) {
//                    break;
//                }

                TimeUnit.SECONDS.sleep(2);

                Elements list = Jsoup.parse(page.content()).select("div[class=LM_list]");
                if (list.size() == 0) {
                    page.close();
                    continue;
                }

                page.evaluateHandle("() =>{ x=document.getElementsByTagName('option'); " +
                        "a=x[x.length-1];" +
                        "a.innerHTML=\"" + size + "\";" +
                        "a.setAttribute('id','" + size + "');}");
                List<String> options = new ArrayList<>();
                options.add(size + "");
                page.select("select[class=pageSizeSelect]", options);
                TimeUnit.SECONDS.sleep(10);
                String content = page.content();
                Document parse = Jsoup.parse(content);
                Elements select = parse.select("div[class=LM_list]");
                if (select.size() > 0) {
                    List<String> docIds = insertDb(select, kw);
                    // 下载附件，最多支持200，所以进行分区下载
                    List<List<String>> partition = ListUtils.partition(docIds, 200);
                    for (List<String> strings : partition) {
                        if (strings.size() > 0) {
                            try {
                                String join = Joiner.on(",").join(strings);
                                page.goTo("https://wenshu.court.gov.cn/down/more?docIds=" + join);
                                TimeUnit.SECONDS.sleep(1);
                            } catch (Exception e) {
                            }
                        }
                    }

                }
                page.close();
            } catch (Exception e) {
                System.err.print(e.toString());
            }
        }
//        }

        if (browser != null) {
            browser.close();
        }
    }

    private static List<String> insertDb(Elements select, String kw) {
        List<String> docIds = new ArrayList<>();
        for (Element element : select) {
            Map<String, String> map = new HashMap<>();
            String slfyName = element.select("span[class=slfyName]").text();
            String ah = element.select("span[class=ah]").text();
            String cprq = element.select("span[class=cprq]").text();
            Elements a = element.select("a[class=caseName]");
            String href = "https://wenshu.court.gov.cn/website/wenshu" + a.attr("href").replace("..", "");
            String caseName = a.text();
            map.put("slfyName", slfyName);
            map.put("ah", ah);
            map.put("cprq", cprq);
            map.put("kw", kw);
            // https://wenshu.court.gov.cn/website/wenshu/181107ANFZ0BXSK4/index
            // .html?docId=6fe8ef1d32a348d6874a3e0a368c3c16
            map.put("url", href);
            // map.put("md5", SecureUtil.md5(href));
            map.put("caseName", caseName);
            // 这里直接输出采集数据
            System.out.println(map.toString());
            docIds.add(href.split("docId")[1]);
        }

        return docIds;
    }
}

