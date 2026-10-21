package com.limelight.binding.video.ahb;

import com.limelight.binding.video.MediaCodecDecoderRenderer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class DecoderCleanupTest {
    @Test public void failedSetupCanCleanUpWithoutCodecOrPresentationThread() {
        MediaCodecDecoderRenderer renderer = mock(MediaCodecDecoderRenderer.class, CALLS_REAL_METHODS);
        renderer.cleanup();
        renderer.cleanup();
    }
}
