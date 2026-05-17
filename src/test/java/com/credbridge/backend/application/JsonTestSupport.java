package com.credbridge.backend.application;

import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MvcResult;

public final class JsonTestSupport {

    private JsonTestSupport() {
    }

    public static Long longValue(MvcResult result, String fieldName) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$." + fieldName);
        return value.longValue();
    }

    public static String stringValue(MvcResult result, String fieldName) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$." + fieldName);
    }
}
