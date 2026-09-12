package com.aacv.system.shared.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrcidIdTests {
    @Test
    void normalizesExistingIdentifiersAndOfficialUrls() {
        assertEquals("0000-0002-1825-0097", OrcidId.normalize(" https://orcid.org/0000-0002-1825-0097 "));
        assertEquals("0000-0000-0000-001X", OrcidId.normalize("0000-0000-0000-001x"));
    }

    @Test
    void rejectsMissingIdentifiersInvalidChecksumsAndOtherHosts() {
        for (String invalid : List.of("", "0000-0002-1825-0098", "0000-0002-1825-00X7",
                "https://evil.test/0000-0002-1825-0097")) {
            assertNull(OrcidId.normalize(invalid));
        }
        assertNull(OrcidId.normalize(null));
    }
}
