package com.yunovan.aiadvent.day19;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class Day19MarketService implements Day19MarketApi {

    private final Day19CatalogService catalog;
    private final Day19SaveService saveService;

    public Day19MarketService(Day19CatalogService catalog, Day19SaveService saveService) {
        this.catalog = catalog;
        this.saveService = saveService;
    }

    @Override
    public List<Day19Product> search(String query, String category, Integer maxResults, String sort) {
        return catalog.search(query, category, maxResults, sort);
    }

    @Override
    public String summarize(String query, String data, String format) {
        List<Day19Product> products = catalog.parseJson(data);
        String fmt = format == null || format.isBlank() ? "markdown" : format.trim().toLowerCase();
        return "csv".equals(fmt) ? Day19TableBuilder.csv(products)
                : Day19TableBuilder.markdown(query, products);
    }

    @Override
    public String summarizeQuery(String query, String format) {
        return summarize(query, productsJson(search(query, null, null, "rating")), format);
    }

    @Override
    public Day19SavedFile saveFile(String data, String summary, String format, String fileName) {
        return saveService.save(summary, data, format, fileName);
    }

    @Override
    public Day19SavedFile saveQuery(String query, String format, String fileName) {
        String data = productsJson(search(query, null, null, "rating"));
        String summary = summarize(query, data, format);
        return saveService.save(summary, data, format, fileName);
    }

    public String productsJson(List<Day19Product> products) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode array = root.putArray("products");
        for (Day19Product product : products) {
            array.add(toNode(mapper, product));
        }
        return root.toString();
    }

    public List<Day19Product> parseProducts(String json) {
        return catalog.parseJson(json);
    }

    private static JsonNode toNode(ObjectMapper mapper, Day19Product product) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", product.id());
        node.put("title", product.title());
        node.put("category", product.category());
        node.put("seller", product.seller());
        node.put("website", product.website());
        node.put("price", product.price());
        node.put("currency", product.currency());
        node.put("rating", product.rating());
        ObjectNode params = node.putObject("params");
        product.params().forEach(params::put);
        return node;
    }
}