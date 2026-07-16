package org.opendroneid.android.ridguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opendroneid.android.data.AircraftObject;
import org.opendroneid.android.data.Identification;
import org.opendroneid.android.data.SystemData;

import java.nio.charset.StandardCharsets;

public class RidGuardAircraftProfileTest {
    @Test
    public void recognizesDocumentedDjiPrefixAndEuClass() {
        AircraftObject aircraft = createAircraft("1581FR1DGUARDDEM0001", 2, 1, 1, 2);

        RidGuardAircraftProfile.Profile profile = RidGuardAircraftProfile.from(aircraft);

        assertEquals("Helikopter/multirotor", profile.uaType);
        assertEquals("Categorie Open", profile.euCategory);
        assertEquals("C1", profile.euClass);
        assertEquals("DJI", profile.manufacturer);
        assertEquals("1581", profile.manufacturerCode);
        assertTrue(profile.serialFormatValid);
    }

    @Test
    public void recognizesDocumentedDronetagPrefix() {
        AircraftObject aircraft = createAircraft("1596FR1DGUARDDEM0002", 4, 1, 2, 7);

        RidGuardAircraftProfile.Profile profile = RidGuardAircraftProfile.from(aircraft);

        assertEquals("Hybrid VTOL", profile.uaType);
        assertEquals("Categorie Specific", profile.euCategory);
        assertEquals("C6", profile.euClass);
        assertEquals("Dronetag", profile.manufacturer);
    }

    @Test
    public void rejectsInvalidCtaLengthAndForbiddenCharacters() {
        assertFalse(RidGuardAircraftProfile.isValidCta2063Serial("1581FTOO-SHORT"));
        assertFalse(RidGuardAircraftProfile.isValidCta2063Serial("1581FABCDEFGHIJKLMNO"));
        assertTrue(RidGuardAircraftProfile.isValidCta2063Serial("1581FR1DGUARDDEM0001"));
    }

    private AircraftObject createAircraft(String serial, int uaType, int classification,
                                          int category, int classValue) {
        AircraftObject aircraft = new AircraftObject(1L);
        Identification identification = new Identification();
        identification.setUaType(uaType);
        identification.setIdType(1);
        identification.setUasId(serial.getBytes(StandardCharsets.US_ASCII));
        aircraft.identification1.setValue(identification);
        aircraft.identification2.setValue(new Identification());

        SystemData system = new SystemData();
        system.setClassificationType(classification);
        system.setCategory(category);
        system.setClassValue(classValue);
        aircraft.system.setValue(system);
        return aircraft;
    }
}
