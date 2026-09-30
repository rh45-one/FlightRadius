package com.flightradius.app.ui.format

import android.content.res.Resources
import androidx.annotation.StringRes
import com.flightradius.app.R
import com.flightradius.app.domain.AlertText

/**
 * Words used by formatting code that has no Compose/Context at hand (notifications,
 * widget, label helpers). Each entry carries an English fallback (used in JVM tests and
 * before [Words.resources] is set) and its string resource.
 */
enum class W(val en: String, @StringRes val res: Int) {
    AGE_NOW("now", R.string.words_age_now),
    AGE_S("%1\$d s ago", R.string.words_age_s),
    AGE_MIN("%1\$d min ago", R.string.words_age_min),
    AGE_H("%1\$d h ago", R.string.words_age_h),
    COVERAGE_LT1("< 1 h", R.string.words_coverage_lt1),
    COVERAGE_DAY("1 day+", R.string.words_coverage_day),
    TREND_STEADY("steady", R.string.words_trend_steady),
    TREND_APPROACHING("approaching", R.string.words_trend_approaching),
    TREND_RECEDING("receding", R.string.words_trend_receding),
    ARROW_APPROACHING("▲ approaching", R.string.words_arrow_approaching),
    ARROW_RECEDING("▼ receding", R.string.words_arrow_receding),
    ALERT_TITLE("✈ %1\$s within %2\$s", R.string.words_alert_title),
    ALERT_BEARING("bearing %1\$s %2\$s", R.string.words_alert_bearing),
    ALERT_CLOSING("closing %1\$s", R.string.words_alert_closing),
    LOC_FIX("GPS%1\$s · %2\$s", R.string.words_loc_fix),
    LOC_SEARCHING("Searching…", R.string.words_loc_searching),
    LOC_MANUAL("Manual", R.string.words_loc_manual),
    LOC_PERMISSION("Permission needed", R.string.words_loc_permission),
    LOC_PROVIDER_OFF("Location off", R.string.words_loc_provider_off),
    LOC_PLAY("Play services missing", R.string.words_loc_play),
    BACKEND_OFFLINE("Offline", R.string.words_backend_offline),
    BACKEND_OK("OpenSky OK", R.string.words_backend_ok),
    BACKEND_NO_CREDITS("Out of credits", R.string.words_backend_no_credits),
    BACKEND_LOGIN_FAILED("OpenSky login failed", R.string.words_backend_login_failed),
    BACKEND_DOWN("OpenSky down", R.string.words_backend_down),
    BACKEND_TIMEOUT("OpenSky timeout", R.string.words_backend_timeout),
    BACKEND_UNREACHABLE_OPENSKY("OpenSky unreachable", R.string.words_backend_unreachable_opensky),
    BACKEND_UNREACHABLE_BACKEND("Backend unreachable", R.string.words_backend_unreachable_backend),
    BACKEND_OPENSKY("OpenSky", R.string.words_backend_opensky),
    BACKEND_BACKEND("Backend", R.string.words_backend_backend),
    CREDITS_K("%1\$sk credits", R.string.words_credits_k),
    CREDITS_N("%1\$d credits", R.string.words_credits_n),
    MON_ON("Monitoring on", R.string.words_mon_on),
    MON_PAUSED("Paused", R.string.words_mon_paused),
    MON_OFF("Monitoring off", R.string.words_mon_off),
    MON_STARTING("Starting…", R.string.words_mon_starting),
    MON_DOZE("Doze-deferred", R.string.words_mon_doze),
    MON_WAITING_LOCATION("Waiting for location", R.string.words_mon_waiting_location),
    MON_ERROR("Error", R.string.words_mon_error),
    NOTIF_PAUSED("Monitoring paused", R.string.words_notif_paused),
    NOTIF_OFFLINE("Offline — retrying in %1\$d s", R.string.words_notif_offline),
    NOTIF_DEFERRED("Deferred (battery saver)", R.string.words_notif_deferred),
    NOTIF_ERROR("Monitoring error", R.string.words_notif_error),
    NOTIF_RUNNING("Monitoring %1\$d aircraft", R.string.words_notif_running),
    NOTIF_STOPPED("Monitoring stopped", R.string.words_notif_stopped),
    NOTIF_UNKNOWN_ERROR("Unknown error", R.string.words_notif_unknown_error),
    NOTIF_CLOSEST("Closest: %1\$s", R.string.words_notif_closest),
    NOTIF_NO_TRACKED("No tracked aircraft", R.string.words_notif_no_tracked),
    NOTIF_NO_AIRCRAFT("No aircraft in range", R.string.words_notif_no_aircraft),
    START_REFUSED("Android refused to start background monitoring: %1\$s", R.string.words_start_refused),
    NEED_MANUAL("Set a manual location first", R.string.words_need_manual),
    NEED_LOCATION("Grant location access to monitor aircraft", R.string.words_need_location),
    NEED_PLAY("Google Play services unavailable — switch to manual location", R.string.words_need_play),
    CH_STATUS("Monitoring status", R.string.words_ch_status),
    CH_ALERTS("Proximity alerts", R.string.words_ch_alerts),
    CH_NEARBY("Nearby aircraft", R.string.words_ch_nearby),
    ACT_SNOOZE("Snooze 30 min", R.string.words_act_snooze),
    ACT_DISMISS("Dismiss", R.string.words_act_dismiss),
    ACT_RESUME("Resume", R.string.words_act_resume),
    ACT_PAUSE("Pause", R.string.words_act_pause),
    ACT_STOP("Stop", R.string.words_act_stop),
    AUX_RESUME_TITLE("Resume monitoring", R.string.words_aux_resume_title),
    AUX_RESUME_TEXT("Tap to restart FlightRadius monitoring.", R.string.words_aux_resume_text),
    AUX_FAIL_TITLE("Monitoring not started", R.string.words_aux_fail_title),
    AUX_TIMEOUT_TITLE("Monitoring stopped", R.string.words_aux_timeout_title),
    AUX_TIMEOUT_TEXT("Android limits background sync in manual-location mode to 6 h/day; open the app to restart.", R.string.words_aux_timeout_text),
    DLG_NOTIF_TITLE("Proximity alert notifications", R.string.words_dlg_notif_title),
    DLG_NOTIF_BODY("Proximity alerts are delivered as notifications. Allow notifications so you don't miss an aircraft entering your alert radius.", R.string.words_dlg_notif_body),
    DLG_CONTINUE("Continue", R.string.words_dlg_continue),
    DLG_LOC_TITLE("Location permission needed", R.string.words_dlg_loc_title),
    DLG_LOC_BODY("GPS monitoring needs location access. Grant it in system settings, or switch to a manual location instead.", R.string.words_dlg_loc_body),
    DLG_OPEN_SETTINGS("Open settings", R.string.words_dlg_open_settings),
    DLG_USE_MANUAL("Use manual location", R.string.words_dlg_use_manual),
    DLG_LAN_TITLE("Nearby devices access", R.string.words_dlg_lan_title),
    DLG_LAN_BODY("Your backend is on your local network. On Android 17+ reaching it requires the 'Nearby devices' permission.", R.string.words_dlg_lan_body),
    DLG_BACKEND_TITLE("Backend unreachable", R.string.words_dlg_backend_title),
    DLG_BACKEND_BODY("Without 'Nearby devices' access the app can't reach your local-network backend. Grant it in app settings to monitor.", R.string.words_dlg_backend_body),
    ERR_ADD("Could not add aircraft", R.string.words_err_add),
    ERR_NETWORK("Network error", R.string.words_err_network),
    ERR_OFFLINE("No internet connection", R.string.words_err_offline),
    ERR_TIMEOUT("Request timed out", R.string.words_err_timeout),
    ERR_TLS("Secure connection failed (certificate not trusted?)", R.string.words_err_tls),
    ERR_BAD_REQUEST("Invalid request", R.string.words_err_bad_request),
    ERR_RATE_LIMITED("OpenSky credits exhausted — waiting for refill", R.string.words_err_rate_limited),
    ERR_AUTH_FAILED("OpenSky rejected the API client credentials", R.string.words_err_auth_failed),
    ERR_OPENSKY_UNAVAILABLE("OpenSky unavailable", R.string.words_err_opensky_unavailable),
    ERR_OPENSKY_TIMEOUT("OpenSky timed out", R.string.words_err_opensky_timeout),
    ERR_SERVER("Server error (%1\$d)", R.string.words_err_server),
    ERR_HTTP("HTTP %1\$d", R.string.words_err_http),
    ERR_MALFORMED("Malformed response", R.string.words_err_malformed),
    ERR_LAN_PERMISSION("Allow 'Nearby devices' access to reach your LAN backend", R.string.words_err_lan_permission),
    ERR_UNKNOWN("Unexpected error", R.string.words_err_unknown),
    RULE_DEFAULT_NAME("Low and close", R.string.words_rule_default_name),
    DB_ERR_HTTP("Download failed (HTTP %1\$d)", R.string.words_db_err_http),
    DB_ERR_LIST("Listing failed (HTTP %1\$d)", R.string.words_db_err_list),
    DB_ERR_NONE("No aircraft database found", R.string.words_db_err_none),
    DB_ERR_EMPTY("The database file had no usable rows", R.string.words_db_err_empty);
}

object Words {
    /** Set from the application; resolves strings in the app's current locale. */
    @Volatile var resources: Resources? = null

    private val EN_COMPASS = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    fun get(w: W, vararg args: Any): String {
        val r = resources
        return if (r != null) r.getString(w.res, *args)
        else String.format(w.en, *args)
    }

    /** Short compass label ("NE"; "NO" in Spanish) for a bearing. */
    fun compass(bearingDeg: Double): String {
        val idx = AlertText.compassIndex(bearingDeg)
        return resources?.getStringArray(R.array.compass_short)?.getOrNull(idx) ?: EN_COMPASS[idx]
    }
}
