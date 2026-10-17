package com.limelight.overlay;

import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayLayoutTest {
    @Test public void defaultsRoundTripAndCustomKeyLabelIsDerived() {
        OverlayLayout layout = OverlayLayout.defaults();
        OverlayLayout.Item shift = new OverlayLayout.Item("shift", "key", .5f, .5f, .12f);
        shift.key = 0xA0; layout.items.add(shift);
        OverlayLayout restored = OverlayLayout.fromJson(layout.toJson());
        assertEquals("Shift", restored.items.get(5).label());
        assertEquals(layout.items.get(2).x, restored.items.get(2).x, 0);
        assertEquals(6, restored.items.size());
    }
    @Test public void rejectsUnsafeOrIncompatibleConfigsWithoutChangingOriginal() {
        OverlayLayout layout = OverlayLayout.defaults();
        String original = layout.toJson();
        layout.version = 999; assertInvalid(layout);
        layout = OverlayLayout.fromJson(original); layout.items.get(0).x = Float.NaN;
        try { layout.validate(); fail(); } catch (IllegalArgumentException expected) {}
        layout = OverlayLayout.fromJson(original); layout.items.remove(1); assertInvalid(layout);
        layout = OverlayLayout.fromJson(original); layout.items.get(1).id = "trackpad"; assertInvalid(layout);
        layout = OverlayLayout.fromJson(original); layout.items.get(1).size = 9; assertInvalid(layout);
        layout = OverlayLayout.fromJson(original); layout.items.get(1).type = "script"; assertInvalid(layout);
        layout = OverlayLayout.fromJson(original);
        OverlayLayout.Item invalid = new OverlayLayout.Item("bad", "key", .5f, .5f, .1f); invalid.key = 65535;
        layout.items.add(invalid); assertInvalid(layout);
        assertEquals(5, OverlayLayout.fromJson(original).items.size());
    }
    private void assertInvalid(OverlayLayout layout) {
        try { OverlayLayout.fromJson(layout.toJson()); fail("Should reject this layout"); }
        catch (IllegalArgumentException expected) {}
    }
}
