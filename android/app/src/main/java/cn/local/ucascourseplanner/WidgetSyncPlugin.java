package cn.local.ucascourseplanner;

import android.content.Context;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;

@CapacitorPlugin(name="WidgetSync")
public class WidgetSyncPlugin extends Plugin {
    @PluginMethod
    public void save(PluginCall call) {
        JSObject data=call.getObject("data");
        if(data==null) data=new JSObject();
        getContext().getSharedPreferences(TimetableWidgetProvider.PREFS,Context.MODE_PRIVATE)
            .edit().putString(TimetableWidgetProvider.KEY,data.toString()).apply();
        TimetableWidgetProvider.refreshAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void clear(PluginCall call) {
        getContext().getSharedPreferences(TimetableWidgetProvider.PREFS,Context.MODE_PRIVATE)
            .edit().remove(TimetableWidgetProvider.KEY).apply();
        TimetableWidgetProvider.refreshAll(getContext());
        call.resolve();
    }
}
