package me.vangoo.infrastructure.market;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class VenueLayoutTest {

    @Test
    void attendeesAreDistinctAndWithinBounds() {
        for (int total = 1; total <= 16; total++) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < total; i++) {
                VenueLayout.Spot s = VenueLayout.attendee(i, total);
                assertTrue(seen.add(s.x() + "," + s.z()),
                        "spot " + i + "/" + total + " duplicated");
                assertTrue(s.x() >= -7.5 && s.x() <= 7.5, "x in bounds: " + s.x());
                assertTrue(s.z() >= -7.5 && s.z() <= 7.5, "z in bounds: " + s.z());
            }
        }
    }

    @Test
    void attendeesStandInFrontOfOrganizerFacingBack() {
        VenueLayout.Spot org = VenueLayout.organizer();
        VenueLayout.Spot a = VenueLayout.attendee(0, 4);
        assertTrue(a.z() > org.z(), "attendees are on the +z side of the organizer");
        // organizer faces +z (toward attendees); attendees face -z (toward organizer)
        assertEquals(0f, org.yaw(), 0.5f);
        assertEquals(180f, a.yaw(), 45f);
    }
}
