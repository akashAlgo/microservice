package com.ewolff.microservice.order;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.stream.StreamSupport;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriTemplate;

import com.ewolff.microservice.order.clients.CatalogClient;
import com.ewolff.microservice.order.clients.Customer;
import com.ewolff.microservice.order.clients.CustomerClient;
import com.ewolff.microservice.order.clients.Item;
import com.ewolff.microservice.order.logic.Order;
import com.ewolff.microservice.order.logic.OrderRepository;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = OrderApp.class)
@WebAppConfiguration
@IntegrationTest
@ActiveProfiles("test")
public class OrderWebIntegrationTest {

	private RestTemplate restTemplate = new RestTemplate();

	@Value("${server.port}")
	private long serverPort;

	@Autowired
	private CatalogClient catalogClient;

	@Autowired
	private CustomerClient customerClient;

	@Autowired
	private OrderRepository orderRepository;

	private Item item;

	private Customer customer;

	@Before
	public void setup() {
		item = catalogClient.findAll().iterator().next();
		customer = customerClient.findAll().iterator().next();
		assertEquals("Eberhard", customer.getFirstname());
	}

	@Test
	public void IsOrderListReturned() {
		try {
			Iterable<Order> orders = orderRepository.findAll();
			assertTrue(StreamSupport
					.stream(orders.spliterator(), false)
					.noneMatch(
							o -> (o.getCustomerId() == customer.getCustomerId())));
			ResponseEntity<String> resultEntity = restTemplate.getForEntity(
					orderURL(), String.class);
			assertTrue(resultEntity.getStatusCode().is2xxSuccessful());
			String orderList = resultEntity.getBody();
			assertFalse(orderList.contains("Eberhard"));
			Order order = new Order(customer.getCustomerId());
			order.addLine(42, item.getItemId());
			orderRepository.save(order);
			orderList = restTemplate.getForObject(orderURL(), String.class);
			assertTrue(orderList.contains("Eberhard"));
		} finally {
			orderRepository.deleteAll();
		}
	}

	private String orderURL() {
		return "http://localhost:" + serverPort;
	}

	@Test
	public void IsOrderFormDisplayed() {
		ResponseEntity<String> resultEntity = restTemplate.getForEntity(
				orderURL() + "/add", String.class);
		assertTrue(resultEntity.getStatusCode().is2xxSuccessful());
		assertTrue(resultEntity.getBody().contains("<form"));
	}

	@Test
	@Transactional
	public void IsSubmittedOrderSaved() {
		long before = orderRepository.count();
		MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
		map.add("submit", "");
		map.add("customerId", Long.toString(customer.getCustomerId()));
		map.add("orderLine[0].itemId", Long.toString(item.getItemId()));
		map.add("orderLine[0].count", "42");
		URI uri = restTemplate.postForLocation(orderURL(), map, String.class);
		UriTemplate uriTemplate = new UriTemplate(orderURL() + "/{id}");
		assertEquals(before + 1, orderRepository.count());
	}
}