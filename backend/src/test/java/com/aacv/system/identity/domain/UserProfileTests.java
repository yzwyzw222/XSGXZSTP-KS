package com.aacv.system.identity.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UserProfileTests {
    @Test
    void normalizesOptionalDataAndAcceptsInternationalPhone() {
        var profile = new UserProfile(" 姓名 ", "person@example.invalid", "+86 (010) 1234-5678", " ", null, "第一行\n第二行");
        assertEquals("姓名", profile.realName());
        assertNull(profile.organization());
        assertEquals("第一行\n第二行", profile.remark());
        assertEquals(UserProfile.EMPTY, new UserProfile(null, "", " ", null, null, null));
    }

    @Test
    void rejectsInvalidAndOversizedDataAtDomainBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new UserProfile("长".repeat(65), null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new UserProfile(null, "not-an-email", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new UserProfile(null, null, "()", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new UserProfile("姓\n名", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new UserProfile(null, null, null, null, null, "长".repeat(501)));
    }
}
