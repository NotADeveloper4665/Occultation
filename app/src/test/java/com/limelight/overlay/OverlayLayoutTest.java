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
    @Test public void clusterButtonsAcceptKeysShortcutsAndBuiltinsAndCanBeDeleted() {
        OverlayLayout layout = OverlayLayout.defaults();
        OverlayLayout.Item keyboard = layout.items.get(2);
        keyboard.assign("key", new int[]{0xA2, 0x43});
        assertEquals("Ctrl + C", keyboard.label());
        OverlayLayout roundTrip = OverlayLayout.fromJson(layout.toJson());
        assertArrayEquals(new int[]{0xA2, 0x43}, roundTrip.items.get(2).keyboardKeys());
        keyboard.assign("home", null); assertEquals("Windows / Super", keyboard.label());
        layout.deleteButton(keyboard.id); layout.validate();
        assertEquals(4, layout.items.size());
        try { layout.deleteButton("eye"); fail(); } catch (IllegalArgumentException expected) {}
    }
    @Test public void versionOneLayoutsUpgradeWithoutLosingKeys() {
        OverlayLayout layout = OverlayLayout.defaults();
        OverlayLayout.Item key = new OverlayLayout.Item("old", "key", .5f, .5f, .1f); key.key = 0xA0; layout.items.add(key);
        layout.version = 1;
        OverlayLayout migrated = OverlayLayout.fromJson(layout.toJson());
        assertEquals(2, migrated.version); assertEquals("Shift", migrated.items.get(5).label());
    }
    @Test public void extraKeysAndRawCodesHaveAutomaticLabels() {
        assertEquals("F24", OverlayKeys.label(0x87));
        assertEquals("Numpad 9", OverlayKeys.label(0x69));
        assertEquals("Play / pause", OverlayKeys.label(0xB3));
        assertEquals("Key 0xE8", OverlayKeys.label(0xE8));
        OverlayLayout layout = OverlayLayout.defaults();
        layout.items.get(2).assign("key", new int[]{0x41,0x41}); assertInvalid(layout);
        layout.items.get(2).assign("trackpad", null); assertInvalid(layout);
    }

}
