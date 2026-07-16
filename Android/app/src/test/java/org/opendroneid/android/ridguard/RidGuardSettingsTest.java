package org.opendroneid.android.ridguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class RidGuardSettingsTest {
    @Test
    public void hashIdIsStableAndPrivacyPreserving() {
        assertEquals("91b89e987c8fe1cc", RidGuardSettings.hashId("test-drone"));
        assertEquals("88c9a4647e18f9de", RidGuardSettings.hashId("DJI-123"));
        assertNotEquals("test-drone", RidGuardSettings.hashId("test-drone"));
    }

    @Test
    public void nullIdProducesEmptyValue() {
        assertEquals("", RidGuardSettings.hashId(null));
    }
}
