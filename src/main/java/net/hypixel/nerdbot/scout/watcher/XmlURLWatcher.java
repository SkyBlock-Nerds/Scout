package net.hypixel.nerdbot.scout.watcher;

import net.hypixel.nerdbot.marmalade.Tuple;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class XmlURLWatcher extends URLWatcher {

    public XmlURLWatcher(String url) {
        super(url);
    }

    public XmlURLWatcher(String url, @Nullable Map<String, String> headers) {
        super(url, headers);
    }

    protected XmlURLWatcher(String url, @Nullable Map<String, String> headers, boolean loadInitialContent) {
        super(url, headers, loadInitialContent);
    }

    @Override
    protected List<Tuple<String, Object, Object>> computeChangedValues(String oldContent, String newContent) {
        return Collections.emptyList();
    }
}
