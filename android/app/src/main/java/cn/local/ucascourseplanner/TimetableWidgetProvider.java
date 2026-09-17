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
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

public class TimetableWidgetProvider extends AppWidgetProvider {

    public static final String PREFS = "ucas_widget";
    public static final String KEY = "timetable_snapshot";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            update(context, manager, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        refreshAll(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();

        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)) {

            refreshAll(context);
        }
    }

    public static void refreshAll(Context context) {
        AppWidgetManager manager =
                AppWidgetManager.getInstance(context);

        ComponentName componentName =
                new ComponentName(context, TimetableWidgetProvider.class);

        int[] ids = manager.getAppWidgetIds(componentName);

        for (int id : ids) {
            update(context, manager, id);
        }
    }

    public static void update(
            Context context,
            AppWidgetManager manager,
            int appWidgetId) {

        RemoteViews views =
                new RemoteViews(
                        context.getPackageName(),
                        R.layout.widget_timetable
                );

        Calendar today = Calendar.getInstance();

        String dateText =
                new SimpleDateFormat(
                        "M月d日",
                        Locale.CHINA
                ).format(today.getTime());

        String raw =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                ).getString(KEY, "");

        JSONObject root = null;

        try {
            if (!raw.isEmpty()) {
                root = new JSONObject(raw);
            }
        } catch (Exception ignored) {
        }

        int teachingWeek = getTeachingWeek(root, today);

        String weekText;

        if (teachingWeek > 0) {
            weekText = "第 " + teachingWeek + " 周";
        } else {
            weekText = "周次未设置";
        }

        views.setTextViewText(
                R.id.widget_title,
                "本周课表"
        );

        views.setTextViewText(
                R.id.widget_date,
                weekText + " · " + dateText
        );

        // 先清空 16 个课程格。
        for (int day = 1; day <= 4; day++) {
            for (int row = 1; row <= 4; row++) {

                int id = getCellId(
                        context,
                        day,
                        row
                );

                views.setTextViewText(id, "—");
            }
        }

        boolean hasCourse = false;

        if (root != null) {

            try {

                JSONArray courses =
                        root.optJSONArray("courses");

                if (courses != null) {

                    Map<String, StringBuilder> cells =
                            new HashMap<>();

                    for (int i = 0;
                         i < courses.length();
                         i++) {

                        JSONObject course =
                                courses.optJSONObject(i);

                        if (course == null) {
                            continue;
                        }

                        String courseName =
                                course
                                        .optString("name", "")
                                        .trim();

                        if (courseName.isEmpty()) {
                            continue;
                        }

                        JSONArray sessions =
                                course.optJSONArray("sessions");

                        if (sessions == null) {
                            continue;
                        }

                        for (int j = 0;
                             j < sessions.length();
                             j++) {

                            JSONObject session =
                                    sessions.optJSONObject(j);

                            if (session == null) {
                                continue;
                            }

                            int day =
                                    session.optInt("day", 0);

                            // 小组件目前显示周一到周四。
                            if (day < 1 || day > 4) {
                                continue;
                            }

                            // 判断当前教学周。
                            JSONArray weeks =
                                    session.optJSONArray("weeks");

                            if (teachingWeek > 0
                                    && weeks != null
                                    && !containsWeek(
                                    weeks,
                                    teachingWeek)) {

                                continue;
                            }

                            JSONArray periods =
                                    session.optJSONArray("periods");

                            if (periods == null) {
                                continue;
                            }

                            String room =
                                    session
                                            .optString("room", "")
                                            .trim();

                            String courseText;

                            if (room.isEmpty()) {
                                courseText = courseName;
                            } else {
                                courseText =
                                        courseName
                                                + "\n"
                                                + room;
                            }

                            /*
                             * 一个课程可能同时占用：
                             * 1、2 节
                             * 或 3、4 节
                             * 等。
                             *
                             * 因此把具体节次转换成：
                             * 1-2 → 第1行
                             * 3-4 → 第2行
                             * 5-6 → 第3行
                             * 7-8 → 第4行
                             */
                            HashSet<Integer> addedRows =
                                    new HashSet<>();

                            for (int k = 0;
                                 k < periods.length();
                                 k++) {

                                int period =
                                        periods.optInt(
                                                k,
                                                0
                                        );

                                if (period <= 0) {
                                    continue;
                                }

                                int row =
                                        (period + 1) / 2;

                                if (row < 1 || row > 4) {
                                    continue;
                                }

                                if (addedRows.contains(row)) {
                                    continue;
                                }

                                addedRows.add(row);

                                String key =
                                        day + ":" + row;

                                StringBuilder text =
                                        cells.get(key);

                                if (text == null) {

                                    text =
                                            new StringBuilder();

                                    cells.put(
                                            key,
                                            text
                                    );
                                }

                                if (text.length() > 0) {
                                    text.append("\n\n");
                                }

                                text.append(courseText);
                            }
                        }
                    }

                    // 把课程写入对应的小组件格子。
                    for (Map.Entry<String, StringBuilder> entry
                            : cells.entrySet()) {

                        String[] parts =
                                entry.getKey().split(":");

                        if (parts.length != 2) {
                            continue;
                        }

                        int day =
                                Integer.parseInt(parts[0]);

                        int row =
                                Integer.parseInt(parts[1]);

                        String text =
                                entry.getValue().toString();

                        // 防止课程文字过长把小组件撑坏。
                        if (text.length() > 120) {
                            text =
                                    text.substring(0, 117)
                                            + "…";
                        }

                        int id =
                                getCellId(
                                        context,
                                        day,
                                        row
                                );

                        views.setTextViewText(
                                id,
                                text
                        );

                        hasCourse = true;
                    }
                }

            } catch (Exception ignored) {
                // 数据异常时保持空课表，不让小组件崩溃。
            }
        }

        if (hasCourse) {

            views.setTextViewText(
                    R.id.widget_status,
                    "点击课表打开 App"
            );

        } else {

            views.setTextViewText(
                    R.id.widget_status,
                    "请打开 App 并先选择课程"
            );
        }

        // 点击整个小组件 → 打开 UCAS 课表 App。
        Intent launchIntent =
                context.getPackageManager()
                        .getLaunchIntentForPackage(
                                context.getPackageName()
                        );

        if (launchIntent != null) {

            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            context,
                            0,
                            launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE
                    );

            views.setOnClickPendingIntent(
                    R.id.widget_root,
                    pendingIntent
            );
        }

        manager.updateAppWidget(
                appWidgetId,
                views
        );
    }

    private static int getCellId(
            Context context,
            int day,
            int row) {

        return context
                .getResources()
                .getIdentifier(
                        "widget_c_"
                                + day
                                + "_"
                                + row,
                        "id",
                        context.getPackageName()
                );
    }

    private static boolean containsWeek(
            JSONArray weeks,
            int targetWeek) {

        for (int i = 0;
             i < weeks.length();
             i++) {

            if (weeks.optInt(
                    i,
                    Integer.MIN_VALUE
            ) == targetWeek) {

                return true;
            }
        }

        return false;
    }

    private static int getTeachingWeek(
            JSONObject root,
            Calendar today) {

        if (root == null) {
            return 0;
        }

        String semesterStart =
                root.optString(
                        "semesterStart",
                        ""
                );

        /*
         * 如果 App 已经保存了开学日期，
         * 根据开学日期自动计算当前教学周。
         */
        if (semesterStart.matches(
                "\\d{4}-\\d{2}-\\d{2}"
        )) {

            try {

                Calendar start =
                        Calendar.getInstance();

                start.setLenient(false);

                start.set(
                        Integer.parseInt(
                                semesterStart.substring(
                                        0,
                                        4
                                )
                        ),
                        Integer.parseInt(
                                semesterStart.substring(
                                        5,
                                        7
                                )
                        ) - 1,
                        Integer.parseInt(
                                semesterStart.substring(
                                        8,
                                        10
                                )
                        ),
                        0,
                        0,
                        0
                );

                start.set(
                        Calendar.MILLISECOND,
                        0
                );

                long difference =
                        today.getTimeInMillis()
                                - start.getTimeInMillis();

                if (difference >= 0) {

                    return (int)
                            (
                                    difference
                                            / (
                                            7L
                                                    * 24L
                                                    * 60L
                                                    * 60L
                                                    * 1000L
                                    )
                            ) + 1;
                }

            } catch (Exception ignored) {
            }
        }

        // 如果没有开学日期，就使用 App 保存的 week。
        return root.optInt("week", 0);
    }
}
