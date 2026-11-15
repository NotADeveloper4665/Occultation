package com.limelight.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class PerformanceDataTrackerTest {
    @Test
    public void historyRetainsOnlyTheMostRecentHundredEntries() throws Exception {
        JSONArray history = new JSONArray();
        for (int i = 0; i < 100; i++) {
            history.put(new JSONObject().put("sequence", i));
        }

        PerformanceDataTracker.appendBounded(
                history,
                new JSONObject().put("sequence", 100)
        );

        assertEquals(100, history.length());
        assertEquals(1, history.getJSONObject(0).getInt("sequence"));
        assertEquals(100, history.getJSONObject(99).getInt("sequence"));
    }

    @Test
    public void historyDoesNotDiscardEntriesBelowTheLimit() throws Exception {
        JSONArray history = new JSONArray();
        history.put(new JSONObject().put("sequence", 1));
        history.put(new JSONObject().put("sequence", 2));

        PerformanceDataTracker.appendBounded(
                history,
                new JSONObject().put("sequence", 3)
        );

        assertEquals(3, history.length());
        assertEquals(1, history.getJSONObject(0).getInt("sequence"));
        assertEquals(3, history.getJSONObject(2).getInt("sequence"));
    }
}
