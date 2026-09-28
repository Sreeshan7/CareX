package com.carex.leave.web;

import com.carex.leave.support.IntegrationTest;
import com.carex.leave.support.TestOrg;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authentication, RBAC, IDOR, mass assignment and error-shape tests over HTTP. */
class SecurityApiTest extends IntegrationTest {
    @Autowired ObjectMapper mapper;

    String submitBody(String start, String end) {
        return """
               {"leaveTypeCode":"CASUAL","startDate":"%s","endDate":"%s","reason":"r","clientRequestId":"%s"}
               """.formatted(start, end, UUID.randomUUID());
    }

    long submitAs(com.carex.leave.auth.CurrentUser u) throws Exception {
        String res = mvc.perform(post("/api/v1/leave-requests").header("Authorization", bearer(u))
                        .contentType(MediaType.APPLICATION_JSON).content(submitBody("2026-10-05", "2026-10-07")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("id").asLong();
    }

    @Test
    void loginSucceedsAndFailsGenerically() throws Exception {
        String res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Arjun@test.carex.app\",\"password\":\"" + TestOrg.PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(res);
        assertThat(node.get("user").get("role").asText()).isEqualTo("EMPLOYEE");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + node.get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("arjun@test.carex.app"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"arjun@test.carex.app\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.carex.app\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action='LOGIN_FAILED'", Integer.class)).isEqualTo(2);
    }

    @Test
    void missingTamperedOrExpiredTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/me/balances")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        String token = bearer(org.arjun);
        mvc.perform(get("/api/v1/me/balances").header("Authorization", token.substring(0, token.length() - 3) + "abc"))
                .andExpect(status().isUnauthorized());
        clock.advance(java.time.Duration.ofHours(9)); // TTL is 8h
        mvc.perform(get("/api/v1/me/balances").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void roleRoutesAreEnforced() throws Exception {
        mvc.perform(get("/api/v1/manager/approvals").header("Authorization", bearer(org.arjun))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/hr/approvals").header("Authorization", bearer(org.arjun))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/hr/approvals").header("Authorization", bearer(org.meera))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/hr/audit").header("Authorization", bearer(org.meera))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/manager/approvals").header("Authorization", bearer(org.meera))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/hr/approvals").header("Authorization", bearer(org.hema))).andExpect(status().isOk());
    }

    @Test
    void idorReadingAnotherEmployeesRequestIs404() throws Exception {
        long id = submitAs(org.arjun);
        mvc.perform(get("/api/v1/leave-requests/" + id).header("Authorization", bearer(org.bala)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/leave-requests/999999").header("Authorization", bearer(org.bala)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        // manager of another team cannot see it either
        mvc.perform(get("/api/v1/leave-requests/" + id).header("Authorization", bearer(org.dev)))
                .andExpect(status().isNotFound());
        // the right manager and HR can
        mvc.perform(get("/api/v1/leave-requests/" + id).header("Authorization", bearer(org.meera)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowedActions[0]").value("MANAGER_APPROVE"));
        mvc.perform(get("/api/v1/leave-requests/" + id).header("Authorization", bearer(org.harish)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowedActions").isEmpty());
        // the owner sees CANCEL only
        mvc.perform(get("/api/v1/leave-requests/" + id).header("Authorization", bearer(org.arjun)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowedActions[0]").value("CANCEL"));
    }

    @Test
    void idorWritingIsBlocked() throws Exception {
        long id = submitAs(org.arjun);
        mvc.perform(post("/api/v1/leave-requests/" + id + "/cancel").header("Authorization", bearer(org.bala))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/leave-requests/" + id + "/decisions").header("Authorization", bearer(org.bala))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"MANAGER\",\"decision\":\"APPROVE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/leave-requests/" + id + "/decisions").header("Authorization", bearer(org.arjun))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"MANAGER\",\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_AN_APPROVER"));
        mvc.perform(post("/api/v1/leave-requests/" + id + "/decisions").header("Authorization", bearer(org.hema))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"HR\",\"decision\":\"APPROVE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.currentStatus").value("PENDING_MANAGER"));
    }

    @Test
    void massAssignmentIsRejected() throws Exception {
        String body = """
                {"leaveTypeCode":"CASUAL","startDate":"2026-10-05","endDate":"2026-10-07","clientRequestId":"%s",
                 "status":"APPROVED","employeeId":%d}
                """.formatted(UUID.randomUUID(), org.bala.id());
        mvc.perform(post("/api/v1/leave-requests").header("Authorization", bearer(org.arjun))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("select count(*) from leave_request", Integer.class)).isZero();
    }

    @Test
    void duplicateHttpSubmitReturnsSameRequest() throws Exception {
        String body = submitBody("2026-10-05", "2026-10-07");
        String a = mvc.perform(post("/api/v1/leave-requests").header("Authorization", bearer(org.arjun))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String b = mvc.perform(post("/api/v1/leave-requests").header("Authorization", bearer(org.arjun))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(header().string("Idempotent-Replay", "true"))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(a).get("id")).isEqualTo(mapper.readTree(b).get("id"));
    }

    @Test
    void validationErrorsHaveProblemShapeWithCorrelationId() throws Exception {
        mvc.perform(post("/api/v1/leave-requests").header("Authorization", bearer(org.arjun)).header("X-Request-Id", "abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"leaveTypeCode\":\"CASUAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void actuatorIsLockedDownExceptHealth() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env").header("Authorization", bearer(org.hema))).andExpect(status().isForbidden());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/actuator/health").secure(true))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Strict-Transport-Security"));
    }

    @Test
    void employeeSeesConflictCountsButNoTeammateNames() throws Exception {
        submitAs(org.arjun);
        String body = """
                {"leaveTypeCode":"ANNUAL","startDate":"2026-10-06","endDate":"2026-10-08","reason":"r","clientRequestId":"%s"}
                """.formatted(UUID.randomUUID());
        String res = mvc.perform(post("/api/v1/leave-requests").header("Authorization", bearer(org.bala))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode n = mapper.readTree(res);
        assertThat(n.get("status").asText()).isEqualTo("PENDING_MANAGER");
        assertThat(n.get("conflict").get("flagged").asBoolean()).isTrue();
        assertThat(n.get("conflict").get("namesVisible").asBoolean()).isFalse();
        assertThat(n.get("conflict").get("days").get(0).get("people").isNull()).isTrue();
        String asManager = mvc.perform(get("/api/v1/leave-requests/" + n.get("id").asLong()).header("Authorization", bearer(org.meera)))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(asManager).get("conflict").get("days").get(0).get("people").toString()).contains("Arjun");
    }

    @Test
    void demoLoginIsDisabledOutsideDemoMode() throws Exception {
        mvc.perform(post("/api/v1/auth/demo-login").contentType(MediaType.APPLICATION_JSON).content("{\"userKey\":\"arjun\"}"))
                .andExpect(status().isNotFound());
    }
}
