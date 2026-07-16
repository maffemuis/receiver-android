/*
 * Copyright (C) 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendroneid.android.ridguard;

import org.opendroneid.android.data.AircraftObject;
import org.opendroneid.android.data.Identification;
import org.opendroneid.android.data.SystemData;

import java.util.Locale;

/**
 * Builds human-readable aircraft information exclusively from Remote ID fields.
 * Manufacturer recognition is intentionally limited to documented CTA manufacturer prefixes.
 */
public final class RidGuardAircraftProfile {
    private RidGuardAircraftProfile() {
    }

    public static final class Profile {
        public final String uaType;
        public final String euCategory;
        public final String euClass;
        public final String idType;
        public final String manufacturer;
        public final String manufacturerCode;
        public final boolean serialFormatValid;

        private Profile(String uaType, String euCategory, String euClass, String idType,
                        String manufacturer, String manufacturerCode, boolean serialFormatValid) {
            this.uaType = uaType;
            this.euCategory = euCategory;
            this.euClass = euClass;
            this.idType = idType;
            this.manufacturer = manufacturer;
            this.manufacturerCode = manufacturerCode;
            this.serialFormatValid = serialFormatValid;
        }

        public String getPrimarySummary() {
            StringBuilder builder = new StringBuilder(uaType);
            if (!euClass.isEmpty()) {
                builder.append(" · ").append(euClass);
            }
            if (!manufacturer.isEmpty()) {
                builder.append(" · ").append(manufacturer);
            }
            return builder.toString();
        }

        public String getSecondarySummary() {
            StringBuilder builder = new StringBuilder();
            if (!euCategory.isEmpty()) {
                builder.append(euCategory);
            }
            if (!idType.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(" · ");
                }
                builder.append(idType);
            }
            if (manufacturer.isEmpty() && !manufacturerCode.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(" · ");
                }
                builder.append("fabrikantcode ").append(manufacturerCode);
            }
            return builder.length() > 0 ? builder.toString() : "Geen extra typegegevens uitgezonden";
        }
    }

    public static Profile from(AircraftObject aircraft) {
        Identification identification = findBestIdentification(aircraft);
        Identification serialIdentification = findSerialIdentification(aircraft);
        SystemData system = aircraft != null ? aircraft.getSystem() : null;

        String uaType = getUaTypeLabel(identification != null
                ? identification.getUaType() : Identification.UaTypeEnum.None);
        String idType = getIdTypeLabel(identification != null
                ? identification.getIdType() : Identification.IdTypeEnum.None);
        String category = getCategoryLabel(system);
        String classLabel = getClassLabel(system);

        String manufacturer = "";
        String manufacturerCode = "";
        boolean serialValid = false;
        if (serialIdentification != null) {
            String serial = normalizeSerial(serialIdentification.getUasIdAsString());
            serialValid = isValidCta2063Serial(serial);
            if (serialValid) {
                manufacturerCode = serial.substring(0, 4);
                manufacturer = getKnownManufacturer(manufacturerCode);
            }
        }

        return new Profile(uaType, category, classLabel, idType,
                manufacturer, manufacturerCode, serialValid);
    }

    public static Identification findBestIdentification(AircraftObject aircraft) {
        if (aircraft == null) {
            return null;
        }
        Identification first = aircraft.getIdentification1();
        Identification second = aircraft.getIdentification2();
        if (first != null && first.getUaType() != Identification.UaTypeEnum.None) {
            return first;
        }
        if (second != null && second.getUaType() != Identification.UaTypeEnum.None) {
            return second;
        }
        if (first != null && first.getIdType() != Identification.IdTypeEnum.None) {
            return first;
        }
        if (second != null && second.getIdType() != Identification.IdTypeEnum.None) {
            return second;
        }
        return first != null ? first : second;
    }

    private static Identification findSerialIdentification(AircraftObject aircraft) {
        if (aircraft == null) {
            return null;
        }
        Identification first = aircraft.getIdentification1();
        if (first != null && first.getIdType() == Identification.IdTypeEnum.Serial_Number) {
            return first;
        }
        Identification second = aircraft.getIdentification2();
        if (second != null && second.getIdType() == Identification.IdTypeEnum.Serial_Number) {
            return second;
        }
        return null;
    }

    public static String normalizeSerial(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\u0000", "").trim().toUpperCase(Locale.US);
    }

    public static boolean isValidCta2063Serial(String value) {
        String serial = normalizeSerial(value);
        if (serial.length() < 6 || serial.length() > 20) {
            return false;
        }
        for (int i = 0; i < serial.length(); i++) {
            char character = serial.charAt(i);
            if (!(character >= '0' && character <= '9')
                    && !(character >= 'A' && character <= 'Z')) {
                return false;
            }
            if (character == 'I' || character == 'O') {
                return false;
            }
        }
        int payloadLength = decodeLengthCode(serial.charAt(4));
        return payloadLength > 0 && serial.length() == payloadLength + 5;
    }

    private static int decodeLengthCode(char code) {
        if (code >= '1' && code <= '9') {
            return code - '0';
        }
        if (code >= 'A' && code <= 'F') {
            return 10 + (code - 'A');
        }
        return -1;
    }

    private static String getKnownManufacturer(String code) {
        switch (code) {
            case "1581":
                return "DJI";
            case "1596":
                return "Dronetag";
            default:
                return "";
        }
    }

    private static String getUaTypeLabel(Identification.UaTypeEnum type) {
        if (type == null) {
            return "Type onbekend";
        }
        switch (type) {
            case Aeroplane:
                return "Fixed-wing";
            case Helicopter_or_Multirotor:
                return "Helikopter/multirotor";
            case Gyroplane:
                return "Gyroplane";
            case Hybrid_Lift:
                return "Hybrid VTOL";
            case Ornithopter:
                return "Ornithopter";
            case Glider:
                return "Zweefvliegtuig";
            case Kite:
                return "Vlieger";
            case Free_balloon:
                return "Vrije ballon";
            case Captive_balloon:
                return "Kabelballon";
            case Airship:
                return "Luchtschip";
            case Free_fall_parachute:
                return "Parachute";
            case Rocket:
                return "Raket";
            case Tethered_powered_aircraft:
                return "Aangelijnd luchtvaartuig";
            case Ground_obstacle:
                return "Grondobstakel";
            case Other:
                return "Overig luchtvaartuig";
            case None:
            default:
                return "Type niet uitgezonden";
        }
    }

    private static String getIdTypeLabel(Identification.IdTypeEnum type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case Serial_Number:
                return "serienummer";
            case CAA_Registration_ID:
                return "registratie-ID";
            case UTM_Assigned_ID:
                return "UTM-ID";
            case Specific_Session_ID:
                return "sessie-ID";
            case None:
            default:
                return "";
        }
    }

    private static String getCategoryLabel(SystemData system) {
        if (system == null || system.getclassificationType() != SystemData.classificationTypeEnum.EU) {
            return "";
        }
        switch (system.getCategory()) {
            case EU_Open:
                return "Categorie Open";
            case EU_Specific:
                return "Categorie Specific";
            case EU_Certified:
                return "Categorie Certified";
            case Undeclared:
            default:
                return "EU-categorie niet uitgezonden";
        }
    }

    private static String getClassLabel(SystemData system) {
        if (system == null || system.getclassificationType() != SystemData.classificationTypeEnum.EU) {
            return "";
        }
        switch (system.getClassValue()) {
            case EU_Class_0:
                return "C0";
            case EU_Class_1:
                return "C1";
            case EU_Class_2:
                return "C2";
            case EU_Class_3:
                return "C3";
            case EU_Class_4:
                return "C4";
            case EU_Class_5:
                return "C5";
            case EU_Class_6:
                return "C6";
            case Undeclared:
            default:
                return "";
        }
    }
}
