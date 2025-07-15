package com.dahe.v2.modules.auth.policy;

import com.dahe.v2.modules.auth.config.AuthProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MiniappLoginScenePolicyTest {

    @Test
    void normalizeScene_shouldKeepAllowedCharsOnly() {
        AuthProperties props = new AuthProperties();
        props.setMiniappLoginScenes("field_record");
        MiniappLoginScenePolicy policy = new MiniappLoginScenePolicy(props);

        Assertions.assertEquals("seed_test.v1", policy.normalizeScene(" Seed_Test.V1 "));
        Assertions.assertEquals("taskcenter", policy.normalizeScene("task@center"));
    }

    @Test
    void isAllowed_shouldValidateAgainstConfiguredWhitelist() {
        AuthProperties props = new AuthProperties();
        props.setMiniappLoginScenes("field_record,seed_test,asset_upload");
        MiniappLoginScenePolicy policy = new MiniappLoginScenePolicy(props);

        Assertions.assertTrue(policy.isAllowed("field_record"));
        Assertions.assertTrue(policy.isAllowed("SEED_TEST"));
        Assertions.assertFalse(policy.isAllowed("other_scene"));
    }
}
