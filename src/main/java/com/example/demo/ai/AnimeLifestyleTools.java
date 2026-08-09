package com.example.demo.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.example.demo.agent.tools.ToolResult;
import com.example.demo.agent.tools.WebSearchTool;
import com.example.demo.chat.entity.Product;
import com.example.demo.ebusiness.ProductService;
import com.example.demo.service.AmapService;
import com.example.demo.weather.model.WeatherResponse;
import com.example.demo.weather.service.WeatherService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class AnimeLifestyleTools {

    private final AmapService amapService;
    private final WeatherService weatherService;
    private final WebSearchTool webSearchTool;
    private final ProductService productService;
    private final AnimeEventService animeEventService;

    public AnimeLifestyleTools(AmapService amapService, WeatherService weatherService,
                               WebSearchTool webSearchTool, ProductService productService,
                               AnimeEventService animeEventService) {
        this.amapService = amapService;
        this.weatherService = weatherService;
        this.webSearchTool = webSearchTool;
        this.productService = productService;
        this.animeEventService = animeEventService;
    }

    @Tool(name = "recommendAnimeRestaurants", description = "Recommend restaurants near a location with an anime-inspired theme and Amap navigation links. Ask location when missing.")
    public String recommendAnimeRestaurants(
            @ToolParam(description = "City, district, venue or business area") String location,
            @ToolParam(description = "Anime title or desired food/theme") String animeTheme) {
        if (location == null || location.isBlank()) return "请先告诉我城市、区县、场馆或商圈，我才能推荐附近餐厅。";
        try {
            AmapService.GeocodeResult origin = amapService.geocode(location);
            String keyword = foodKeyword(animeTheme);
            List<AmapService.PoiItem> restaurants = amapService.searchNearbyPois(
                    origin.getLng() + "," + origin.getLat(), keyword, 5000, 10);
            if (restaurants.isEmpty()) return "附近没有找到符合“" + keyword + "”的餐厅，可以换一个口味。";
            StringBuilder result = new StringBuilder("根据《").append(animeTheme).append("》风格，为你搜索：")
                    .append(keyword).append("\n位置：").append(origin.getFormattedAddress()).append("\n\n");
            for (int index = 0; index < Math.min(5, restaurants.size()); index++) {
                AmapService.PoiItem restaurant = restaurants.get(index);
                result.append(index + 1).append(". ").append(restaurant.getName()).append("\n   地址：")
                        .append(restaurant.getAddress());
                if (restaurant.getDistance() != null) result.append("｜距离：").append(restaurant.getDistance()).append("米");
                if (restaurant.getTel() != null && !restaurant.getTel().isBlank()) result.append("｜电话：").append(restaurant.getTel());
                result.append("\n   高德导航：").append(amapService.buildWebNavigationUrl(
                        origin.getLng(), origin.getLat(), location,
                        restaurant.getLng(), restaurant.getLat(), restaurant.getName())).append("\n\n");
            }
            result.append("大众点评/美团没有稳定公开的排队取号 API；如餐厅提供电话，建议先电话确认排队和营业状态。 ");
            return result.toString();
        } catch (Exception error) {
            return "餐厅查询失败：" + error.getMessage();
        }
    }

    @Tool(name = "searchAnimeEventsChina", description = "Multi-source real-time Chinese anime event intelligence. Search confirmed dates, cities, venues and prices; discover organizer accounts and official announcements; cross-check Bilibili schedules, guests, postponements, ticket clues and social-platform source links. Preserve exact event names. Use for any convention, doujin event, anime festival and follow-up question; results may take longer because multiple live sources are queried.")
    public String searchAnimeEventsChina(
            @ToolParam(description = "City or region") String city,
            @ToolParam(description = "IP, guest, event type or date keywords") String keywords) {
        if (animeEventService != null) return animeEventService.searchIntelligence(city, keywords, 8);
        String query = (city == null ? "" : city) + " " + (keywords == null ? "" : keywords)
                + " 漫展 同人展 动漫演唱会 票务 2026 site:bilibili.com OR site:damai.cn OR site:showstart.com OR site:cpp.cn";
        JSONObject params = JSONObject.of("query", query.trim());
        ToolResult<String> result = webSearchTool.execute(params);
        if (!result.isSuccess()) return "活动查询失败：" + result.getMessage();
        return result.getData() + "\n请告诉我你感兴趣的活动，我可以继续查询场馆、高德路线、天气和周边餐厅。";
    }

    @Tool(name = "recommendAnimeMerchandise", description = "Search the local anime merchandise store by IP, character and product type.")
    public String recommendAnimeMerchandise(
            @ToolParam(description = "Anime IP or character") String ipOrCharacter,
            @ToolParam(description = "Product type, e.g. figure, badge, acrylic stand") String productType) {
        String query = ((ipOrCharacter == null ? "" : ipOrCharacter) + " " + (productType == null ? "" : productType)).trim();
        List<Product> products = productService.findAll().stream()
                .filter(product -> "ON".equalsIgnoreCase(product.getStatus()))
                .filter(product -> contains(product.getName(), query) || contains(product.getDescription(), ipOrCharacter)
                        || contains(product.getDetail(), ipOrCharacter))
                .limit(8).toList();
        if (products.isEmpty()) return "本地商城暂时没有匹配“" + query + "”的周边。要不要我改为联网查官方周边信息？";
        StringBuilder result = new StringBuilder("商城周边推荐\n\n");
        for (Product product : products) {
            result.append("- ").append(product.getName()).append("｜￥").append(product.getPrice())
                    .append("｜库存 ").append(product.getStock()).append("\n  详情：http://localhost:8094/shop.html?productId=")
                    .append(product.getId()).append("\n");
        }
        return result.toString();
    }

    @Tool(name = "planAnimeEventTrip", description = "Combine weather and Amap venue location for an anime convention or outdoor event trip.")
    public String planAnimeEventTrip(
            @ToolParam(description = "City") String city,
            @ToolParam(description = "Venue or event address") String venue,
            @ToolParam(description = "Event date in YYYY-MM-DD") String date) {
        if (city == null || city.isBlank() || venue == null || venue.isBlank()) return "请提供城市和场馆名称。";
        try {
            WeatherResponse weather = weatherService.getWeatherByCity(city);
            AmapService.GeocodeResult place = amapService.geocode(venue, city);
            return "漫展出行方案（" + date + "）\n场馆：" + place.getFormattedAddress()
                    + "\n高德地图：" + amapService.buildMarkerUrl(place.getLng(), place.getLat(), venue)
                    + "\n天气数据：" + JSON.toJSONString(weather)
                    + "\n建议：根据降雨、温度准备雨具、防晒和补水；临近出发前再次确认天气与活动公告。";
        } catch (Exception error) {
            return "行程规划失败：" + error.getMessage();
        }
    }

    @Tool(name = "recommendGamesFromAnime", description = "Recommend games or films based on an anime's themes, combat style and world setting, using real-time web sources.")
    public String recommendGamesFromAnime(
            @ToolParam(description = "Anime title") String animeTitle,
            @ToolParam(description = "Platform such as PC, PS5, Switch or mobile") String platform) {
        JSONObject params = JSONObject.of("query", animeTitle + " similar games recommendation " + platform
                + " official Steam PlayStation Nintendo 2026");
        ToolResult<String> result = webSearchTool.execute(params);
        return result.isSuccess() ? result.getData() + "\n告诉我更看重世界观、战斗、剧情还是难度，我可以继续筛选。"
                : "游戏推荐查询失败：" + result.getMessage();
    }

    private String foodKeyword(String animeTheme) {
        if (animeTheme == null) return "特色餐厅";
        if (animeTheme.contains("食戟") || animeTheme.contains("中华一番")) return "创意料理 日料";
        if (animeTheme.contains("孤独美食家")) return "当地特色 小馆";
        if (animeTheme.contains("摇曳露营")) return "烧烤 露营餐厅";
        if (animeTheme.contains("女仆") || animeTheme.contains("咖啡")) return "女仆咖啡 动漫主题餐厅";
        return animeTheme + " 主题餐厅";
    }

    private boolean contains(String value, String keyword) {
        return value != null && keyword != null && !keyword.isBlank() && value.toLowerCase().contains(keyword.toLowerCase());
    }
}
