package com.limelight.overlay;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 33)
public class OverlayStoreTest {
    private OverlayStore store;
    private Context context;
    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("desktop_overlay_layouts", 0).edit().clear().commit(); store = new OverlayStore(context);
    }
    @Test public void savesSelectsAndMatchesGamesWithoutOverwritingImports() {
        OverlayLayout desktop = store.active(); store.save(desktop);
        OverlayLayout game = desktop.copy(); game.id = "game1"; game.name = "Game layout"; game.game = "Example Game";
        store.save(game);
        assertEquals(desktop.id, store.forGame("Another game").id);
        store.save(desktop);
        assertEquals(desktop.id, store.forGame("Desktop").id);
        assertEquals(game.id, store.forGame("example game").id);
        OverlayLayout imported = store.importLayout(game.toJson());
        assertNotEquals(game.id, imported.id);
        assertEquals(2, store.layouts().size()); // Previewing an import doesn't mutate saved layouts.
        store.save(imported);
        assertEquals(3, store.layouts().size());
        assertEquals(imported.id, store.forGame("Example Game").id);
        store.delete(imported.id);
        assertFalse(store.layouts().isEmpty());
    }
    @Test public void brokenStorageFallsBackAndDeletingLastKeepsAUsableLayout() {
        context.getSharedPreferences("desktop_overlay_layouts", 0).edit().putString("layouts", "not JSON").commit();
        OverlayLayout safe = store.active(); safe.validate();
        store.delete(safe.id); assertEquals(1, store.layouts().size());
    }
}
