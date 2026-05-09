package com.credbridge.backend.application;

import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MvcResult;

final class JsonTestSupport {

    private JsonTestSupport() {
    }

    static Long longValue(MvcResult result, String fieldName) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$." + fieldName);
        return value.longValue();
    }
}
