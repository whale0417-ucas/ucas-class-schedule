package cn.local.ucascourseplanner;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TimetableWidgetProvider extends AppWidgetProvider {
    public static final String PREFS = "ucas_widget";
    public static final String KEY = "timetable_snapshot";

    private static final String[] DAY_NAMES = {"周一", "周二", "周三", "周四"};
    private static final String[] PERIOD_NAMES = {"1-2", "3-4", "5-6", "7-8"};

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    @Override public void onEnabled(Context c) {
        super.onEnabled(c);
        refreshAll(c);
    }

    @Override public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction()) ||
            Intent.ACTION_TIME_CHANGED.equals(intent.getAction()) ||
            Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction()) ||
            Intent.ACTION_DATE_CHANGED.equals(intent.getAction())) {
            refreshAll(c);
        }
    }

    public static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        ComponentName n = new ComponentName(c, TimetableWidgetProvider.class);
        for (int id : m.getAppWidgetIds(n)) update(c, m, id);
    }

    public static void update(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_timetable);
        Calendar cal = Calendar.getInstance();
        String date = new SimpleDateFormat("M月d日", Locale.CHINA).format(cal.getTime());

        String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        JSONObject root = null;
        try { if (!raw.isEmpty()) root = new JSONObject(raw); } catch (Exception ignored) {}

        int teachingWeek = getTeachingWeek(root, cal);
        String weekText = teachingWeek > 0 ? "第 " + teachingWeek + " 周" : "周次未设置";
        v.setTextViewText(R.id.widget_title, "本周课表");
        v.setTextViewText(R.id.widget_date, weekText + " · " + date);

        for (int d = 1; d <= 4; d++) {
            for (int r = 1; r <= 4; r++) {
                int cid = cellId(c, d, r);
                v.setTextViewText(cid, "—");
            }
        }

        boolean any = false;
        if (root != null) {
            try {
                JSONArray courses = root.optJSONArray("courses");
                if (courses != null) {
                    Map<String, StringBuilder> cells = new HashMap<>();
                    for (int i = 0; i < courses.length(); i++) {
                        JSONObject course = courses.optJSONObject(i);
                        if (course == null) continue;
                        String name = course.optString("name", "").trim();
                        if (name.isEmpty()) continue;
                        JSONArray sessions = course.optJSONArray("sessions");
                        if (sessions == null) continue;
                        for (int j = 0; j < sessions.length(); j++) {
                            JSONObject s = sessions.optJSONObject(j);
                            if (s == null || s.optInt("day", 0) < 1 || s.optInt("day", 0) > 4) continue;
                            JSONArray weeks = s.optJSONArray("weeks");
                            if (teachingWeek > 0 && weeks != null && !contains(weeks, teachingWeek)) continue;
                            JSONArray periods = s.optJSONArray("periods");
                            if (periods == null) continue;
                            String room = s.optString("room", "").trim();
                            String text = name + (room.isEmpty() ? "" : "\n" + room);
                            int day = s.optInt("day");
                            java.util.HashSet<Integer> addedRows = new java.util.HashSet<>();
                            for (int k = 0; k < periods.length(); k++) {
                                int period = periods.optInt(k, 0);
                                int row = (period + 1) / 2;
                                if (row < 1 || row > 4 || addedRows.contains(row)) continue;
                                addedRows.add(row);
                                String key = day + ":" + row;
                                StringBuilder sb = cells.get(key);
                                if (sb == null) { sb = new StringBuilder(); cells.put(key, sb); }
                                if (sb.length() > 0) sb.append("\n\n");
                                sb.append(text);
                            }
                        }
                    }
                    for (Map.Entry<String, StringBuilder> e : cells.entrySet()) {
                        String[] parts = e.getKey().split(":");
                        int day = Integer.parseInt(parts[0]);
                        int row = Integer.parseInt(parts[1]);
                        String text = e.getValue().toString();
                        if (text.length() > 120) text = text.substring(0, 117) + "…";
                        v.setTextViewText(cellId(c, day, row), text);
                        any = true;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (!any) v.setTextViewText(R.id.widget_status, "请打开 App 并先选择课程");
        else v.setTextViewText(R.id.widget_status, "点击课表打开 App");

        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch != null) {
            PendingIntent pi = PendingIntent.getActivity(c, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.widget_root, pi);
        }
        m.updateAppWidget(id, v);
    }

    private static int cellId(Context c, int day, int row) {
        return c.getResources().getIdentifier("widget_c_" + day + "_" + row, "id", c.getPackageName());
    }

    private static boolean contains(JSONArray a, int value) {
        for (int i = 0; i < a.length(); i++) if (a.optInt(i, Integer.MIN_VALUE) == value) return true;
        return false;
    }

    private static int getTeachingWeek(JSONObject root, Calendar today) {
        if (root == null) return 0;
        String start = root.optString("semesterStart", "");
        if (start.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                Calendar s = Calendar.getInstance();
                s.setLenient(false);
                s.set(Integer.parseInt(start.substring(0,4)), Integer.parseInt(start.substring(5,7)) - 1,
                    Integer.parseInt(start.substring(8,10)), 0, 0, 0);
                s.set(Calendar.MILLISECOND, 0);
                long diff = today.getTimeInMillis() - s.getTimeInMillis();
                if (diff >= 0) return (int)(diff / (7L * 24L * 60L * 60L * 1000L)) + 1;
            } catch (Exception ignored) {}
        }
        return root.optInt("week", 0);
    }
}
