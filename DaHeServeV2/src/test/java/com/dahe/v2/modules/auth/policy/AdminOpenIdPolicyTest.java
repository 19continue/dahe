package com.dahe.v2.modules.auth.policy;

import com.dahe.v2.modules.auth.config.AuthProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class AdminOpenIdPolicyTest {

    @Test
    void isAdminOpenIdLike_shouldMatchConfiguredPrefixes() {
        AuthProperties props = new AuthProperties();
        props.setAdminOpenIdDetectionPrefixes(Arrays.asList("admin_", "mgr_"));
        props.setAdminOpenIdGeneratePrefix("mgr_");
        AdminOpenIdPolicy policy = new AdminOpenIdPolicy(props);

        Assertions.assertTrue(policy.isAdminOpenIdLike("admin_abc"));
        Assertions.assertTrue(policy.isAdminOpenIdLike("MGR_XYZ"));
        Assertions.assertFalse(policy.isAdminOpenIdLike("user_abc"));
    }

    @Test
    void generateAdminOpenId_shouldUseConfiguredPrefix() {
        AuthProperties props = new AuthProperties();
        props.setAdminOpenIdGeneratePrefix("root_");
        AdminOpenIdPolicy policy = new AdminOpenIdPolicy(props);

        String openId = policy.generateAdminOpenId();
        Assertions.assertTrue(openId.startsWith("root_"));
        Assertions.assertTrue(openId.length() > "root_".length());
    }
}
