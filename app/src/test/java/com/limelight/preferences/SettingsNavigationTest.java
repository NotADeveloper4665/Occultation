package com.limelight.preferences;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.preference.*;
import androidx.test.core.app.ApplicationProvider;
import com.limelight.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 33)
public class SettingsNavigationTest {
    @Test public void commonViewAndSearchRetainTheFullPreferenceHierarchy() {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.SettingsTheme);
        PreferenceScreen screen = new PreferenceManager(context).createPreferenceScreen(context);
        PreferenceCategory video = new PreferenceCategory(context); video.setKey("category_video_settings"); screen.addPreference(video);
        Preference fps = new Preference(context); fps.setKey("list_fps"); fps.setTitle("Frame rate"); video.addPreference(fps);
        Preference hdr = new Preference(context); hdr.setKey("checkbox_enable_hdr"); hdr.setTitle("HDR"); video.addPreference(hdr);
        SettingsNavigation navigation = new SettingsNavigation(screen);
        LinearLayout root = (LinearLayout) navigation.wrap(context, new View(context));
        assertTrue(fps.isVisible()); assertFalse(hdr.isVisible());
        ((EditText) root.getChildAt(0)).setText("HDR");
        assertTrue(hdr.isVisible()); assertFalse(fps.isVisible());
        assertSame(hdr, screen.findPreference("checkbox_enable_hdr"));
        ((EditText) root.getChildAt(0)).setText("");
        assertTrue(fps.isVisible()); assertFalse(hdr.isVisible());
    }
}
