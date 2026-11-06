package com.example.odata.controller;

import com.example.odata.model.CustomerOrderResponse;
import com.example.odata.service.OrderService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/odata")
public class ODataController {

    private static final Pattern CUSTOMER_FILTER =
            Pattern.compile("(?i)^CustomerId\\s+eq\\s+'((?:[^']|'')*)'$");

    private final OrderService service;

    public ODataController(OrderService service) {
        this.service = service;
    }

    @GetMapping(value = {"", "/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> serviceDocument() {
        Map<String, Object> entitySet = new LinkedHashMap<>();
        entitySet.put("name", "CustomerOrders");
        entitySet.put("kind", "EntitySet");
        entitySet.put("url", "CustomerOrders");

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("@odata.context", "$metadata");
        document.put("value", List.of(entitySet));
        return document;
    }

    @GetMapping(value = "/$metadata", produces = MediaType.APPLICATION_XML_VALUE)
    public String metadata() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <edmx:Edmx Version="4.0"
                    xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">
                  <edmx:DataServices>
                    <Schema Namespace="PostgresService"
                        xmlns="http://docs.oasis-open.org/odata/ns/edm">
                      <EntityType Name="CustomerOrder">
                        <Key>
                          <PropertyRef Name="OrderId"/>
                        </Key>
                        <Property Name="OrderId" Type="Edm.String" Nullable="false"/>
                        <Property Name="CustomerId" Type="Edm.String" Nullable="false"/>
                        <Property Name="ProductName" Type="Edm.String"/>
                        <Property Name="Amount" Type="Edm.Decimal" Precision="12" Scale="2"/>
                        <Property Name="Status" Type="Edm.String"/>
                        <Property Name="OrderDate" Type="Edm.DateTimeOffset"/>
                      </EntityType>
                      <EntityContainer Name="Container">
                        <EntitySet Name="CustomerOrders"
                            EntityType="PostgresService.CustomerOrder"/>
                      </EntityContainer>
                    </Schema>
                  </edmx:DataServices>
                </edmx:Edmx>
                """;
    }

    @GetMapping(value = "/CustomerOrders", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getOrders(
            @RequestParam(name = "$filter", required = false) String filter,
            @RequestParam(name = "$top", defaultValue = "100") int top,
            @RequestParam(name = "$skip", defaultValue = "0") int skip) {

        if (top < 0 || skip < 0) {
            throw new IllegalArgumentException("$top and $skip must be zero or greater.");
        }

        List<CustomerOrderResponse> rows;
        if (filter == null || filter.isBlank()) {
            rows = service.findAll();
        } else {
            Matcher matcher = CUSTOMER_FILTER.matcher(filter.trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "This demo supports only $filter=CustomerId eq 'CUST-1001'.");
            }
            String customerId = matcher.group(1).replace("''", "'");
            rows = service.findByCustomerId(customerId);
        }

        int safeTop = Math.min(top, 500);
        List<Map<String, Object>> values = rows.stream()
                .skip(skip)
                .limit(safeTop)
                .map(this::toODataMap)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("@odata.context", "$metadata#CustomerOrders");
        response.put("value", values);
        return response;
    }

    @GetMapping(value = "/CustomerOrders('{orderId}')",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getOrder(@PathVariable String orderId) {
        return toODataMap(service.findById(orderId));
    }

    private Map<String, Object> toODataMap(CustomerOrderResponse order) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("OrderId", order.getOrderId());
        item.put("CustomerId", order.getCustomerId());
        item.put("ProductName", order.getProductName());
        item.put("Amount", order.getAmount());
        item.put("Status", order.getStatus());
        item.put("OrderDate", order.getOrderDate());
        return item;
    }
}
