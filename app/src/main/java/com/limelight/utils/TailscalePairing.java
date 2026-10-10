package com.limelight.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import java.net.InetAddress;
import java.io.IOException;

public final class TailscalePairing {
    private TailscalePairing() { }
    public static boolean isTailscaleAddress(byte[] ip) {
        if (ip.length == 4) return (ip[0] & 255) == 100 && ((ip[1] & 255) & 192) == 64;
        return ip.length == 16 && (ip[0] & 255) == 0xfd && (ip[1] & 255) == 0x7a &&
                (ip[2] & 255) == 0x11 && (ip[3] & 255) == 0x5c &&
                (ip[4] & 255) == 0xa1 && (ip[5] & 255) == 0xe0;
    }
    // Run on the pairing worker. Advertising auto-pairing is not sufficient:
    // require a VPN route and a Tailscale address before sending enrollment.
    public static boolean protectedConnection(Context context, String host) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                isTailscaleAddress(InetAddress.getByName(host).getAddress());
    }
}
