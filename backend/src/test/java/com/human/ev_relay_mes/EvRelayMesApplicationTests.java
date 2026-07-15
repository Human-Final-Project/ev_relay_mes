package com.human.ev_relay_mes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "L2_API_KEY=test-l2-key")
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
	void rejectsL2RequestWithoutApiKey() throws Exception {
		mockMvc.perform(post("/api/collector/production-logs")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "lotNo": "LOT-001",
							  "machineId": "MC-001",
							  "processCode": "OP10",
							  "inputQty": 10,
							  "okQty": 10,
							  "ngQty": 0,
							  "status": "COMPLETED"
							}
							"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("M002"));
	}

	@Test
	void rejectsL2DefectWithoutApiKey() throws Exception {
		mockMvc.perform(post("/api/collector/defects")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "lotNo": "EVR-LOT-20260708-001",
							  "machineId": "EQ-WELD-01",
							  "processCode": "OP30",
							  "defectCode": "WELD_STRENGTH_NG",
							  "defectQty": 3,
							  "message": "Weld strength below limit"
							}
							"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("M002"));
	}

	@Test
	void rejectsCollectorCommandAckWithoutApiKey() throws Exception {
		mockMvc.perform(post("/api/collector/command-acks")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "machineId": "EQ-WIND-01",
							  "commandId": 101,
							  "ackStatus": "ACCEPTED",
							  "message": null
							}
							"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("M002"));
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
