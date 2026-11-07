package com.ayubase6.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AnnouncementCheckTest {
    @Test public void expiry() {
        assertFalse(AnnouncementCheck.expired(""));
        assertFalse(AnnouncementCheck.expired("not a date"));
        assertTrue(AnnouncementCheck.expired("05-Nov-2020"));
        assertTrue(AnnouncementCheck.expired("2020-11-05"));
        assertFalse(AnnouncementCheck.expired("05-nov-2099"));
        assertFalse(AnnouncementCheck.expired("2099-01-01"));
    }
}
