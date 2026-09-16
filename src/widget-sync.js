/**
 * Copy the active plan and the real course catalog fields from WebView localStorage
 * into native SharedPreferences so the Android home-screen widget can render the
 * same timetable as the app. The native side handles current teaching-week filtering.
 */
export async function syncTimetableWidget() {
  try {
    const key = window.__UCAS_STORAGE_KEY__ || Object.keys(localStorage).find(k => k.startsWith('ucas-planner-v1-'));
    if (!key) return;
    const state = JSON.parse(localStorage.getItem(key) || '{}');
    const plans = Array.isArray(state.plans) ? state.plans : [];
    const active = plans.find(p => p.id === state.activeId) || plans[0];
    if (!active) return;

    const catalog = Array.isArray(window.__UCAS_COURSES__) ? window.__UCAS_COURSES__ : [];
    const byId = new Map(catalog.map(c => [String(c.id), c]));
    const courses = (Array.isArray(active.ids) ? active.ids : [])
      .map(id => byId.get(String(id)))
      .filter(Boolean)
      .map(c => ({
        id: String(c.id ?? ''),
        name: String(c.name ?? ''),
        sessions: Array.isArray(c.sessions) ? c.sessions.map(s => ({
          day: Number(s.day ?? 0),
          periods: Array.isArray(s.periods) ? s.periods.map(Number).filter(Number.isFinite) : [],
          room: String(s.room ?? ''),
          weeks: Array.isArray(s.weeks) ? s.weeks.map(Number).filter(Number.isFinite) : []
        })) : []
      }));

    const { registerPlugin } = await import('@capacitor/core');
    const WidgetSync = registerPlugin('WidgetSync');
    await WidgetSync.save({ data: {
      version: 2,
      week: Number(state.week || 0),
      semesterStart: String(state.semesterStart || ''),
      courses
    }});
  } catch (e) {
    console.warn('WidgetSync unavailable', e);
  }
}
