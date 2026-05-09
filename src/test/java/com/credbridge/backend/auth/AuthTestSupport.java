package com.credbridge.backend.auth;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class AuthTestSupport {

    private AuthTestSupport() {
    }

    public static String registerAndLogin(MockMvc mockMvc, UserRole role) throws Exception {
        String email = uniqueEmail(role.name().toLowerCase());
        String password = "strongpass123";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s User",
                                  "email": "%s",
                                  "password": "%s",
                                  "role": "%s"
                                }
                                """.formatted(role.name(), email, password, role.name())))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        return "Bearer " + token;
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@credbridge.test";
    }
}
