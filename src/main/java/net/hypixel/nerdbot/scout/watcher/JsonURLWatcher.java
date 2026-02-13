package net.hypixel.nerdbot.scout.watcher;

import net.hypixel.nerdbot.marmalade.json.JsonUtils;
import net.hypixel.nerdbot.marmalade.Tuple;

import java.util.List;
import java.util.Map;

public class JsonURLWatcher extends URLWatcher {

    public JsonURLWatcher(String url) {
        super(url);
    }

    public JsonURLWatcher(String url, Map<String, String> headers) {
        super(url, headers);
    }

    protected JsonURLWatcher(String url, Map<String, String> headers, boolean loadInitialContent) {
        super(url, headers, loadInitialContent);
    }

    @Override
    protected List<Tuple<String, Object, Object>> computeChangedValues(String oldContent, String newContent) {
        return JsonUtils.findChangedValues(
            JsonUtils.parseStringToMap(oldContent),
            JsonUtils.parseStringToMap(newContent),
            ""
        );
    }
}
