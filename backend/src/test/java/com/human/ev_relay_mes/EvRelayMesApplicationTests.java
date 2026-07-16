package com.human.ev_relay_mes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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

}
