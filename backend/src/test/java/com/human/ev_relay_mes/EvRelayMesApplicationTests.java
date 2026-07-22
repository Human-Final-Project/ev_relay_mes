package com.human.ev_relay_mes;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvRelayMesApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void rejectsUnauthenticatedReactApiRequest() throws Exception {
		mockMvc.perform(get("/api/mes/dashboard/summary"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void collectorApiDoesNotRequireApiKeyButStillValidatesRequestBody() throws Exception {
		mockMvc.perform(post("/api/collector/production-logs")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void exposesSwaggerUiAndOpenApiDocsWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection());

		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").exists());
	}

	@Test
	void acceptsCookieCsrfTokenSentBySwagger() throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andReturn();
		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
		assertNotNull(csrfCookie);

		mockMvc.perform(post("/api/auth/login")
					.cookie(csrfCookie)
					.header("X-XSRF-TOKEN", csrfCookie.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"loginId\":\"unknown-user\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(roles = "VIEWER")
	void returnsDashboardSummaryToAuthenticatedUser() throws Exception {
		mockMvc.perform(get("/api/mes/dashboard/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.production").exists())
				.andExpect(jsonPath("$.workOrders").exists())
				.andExpect(jsonPath("$.machines").exists())
				.andExpect(jsonPath("$.quality").exists())
				.andExpect(jsonPath("$.alarms").exists())
				.andExpect(jsonPath("$.materials").exists());
	}

	@Test
	void acceptsCollectorConnectionStatusWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/collector/status")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"collectorId\":\"L2-COLLECTOR-01\",\"connectedL1\":4,\"totalL1\":6}"))
				.andExpect(status().isNoContent());
	}

	@Test
	@WithMockUser(roles = "VIEWER")
	void returnsSystemConnectionStatusToAuthenticatedUser() throws Exception {
		mockMvc.perform(post("/api/collector/status")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"collectorId\":\"L2-COLLECTOR-01\",\"connectedL1\":4,\"totalL1\":6}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/mes/system-connections"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.l2.status").value("ONLINE"))
				.andExpect(jsonPath("$.l1.status").value("PARTIAL"))
				.andExpect(jsonPath("$.l1.connected").value(4))
				.andExpect(jsonPath("$.l1.total").value(6));
	}

	@Test
	@WithMockUser(roles = "VIEWER")
	void bindsOptionalWorkOrderAndLotFilters() throws Exception {
		mockMvc.perform(get("/api/work-orders"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/work-orders").param("status", "CREATED"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/lots"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/lots").param("status", "WAITING"))
				.andExpect(status().isOk());
	}

}
