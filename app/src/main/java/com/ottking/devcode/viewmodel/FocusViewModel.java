package com.ottking.devcode.viewmodel;

import androidx.lifecycle.ViewModel;

import java.util.HashMap;
import java.util.Map;

/**
 * ViewModel responsible for retaining the last focused item's ID, Tag, or position per screen.
 * Survives configuration changes and screen transitions.
 */
public class FocusViewModel extends ViewModel {

    private final Map<String, Integer> lastFocusedViewIds = new HashMap<>();
    private final Map<String, String> lastFocusedTags = new HashMap<>();
    private final Map<String, Map<String, Integer>> lastFocusedGroupPositions = new HashMap<>();
    private final Map<String, Map<String, Integer>> lastFocusedGroupItemIds = new HashMap<>();
    private final Map<String, Map<String, String>> lastFocusedGroupItemStringIds = new HashMap<>();
    private final Map<String, String> lastFocusedGroupKeys = new HashMap<>();

    public void saveFocusId(String screenKey, int viewId) {
        if (viewId != 0 && viewId != -1) {
            lastFocusedViewIds.put(screenKey, viewId);
        }
    }

    public int getLastFocusId(String screenKey, int defaultId) {
        Integer id = lastFocusedViewIds.get(screenKey);
        return id != null ? id : defaultId;
    }

    public void saveFocusTag(String screenKey, String tag) {
        if (tag != null && !tag.isEmpty()) {
            lastFocusedTags.put(screenKey, tag);
        }
    }

    public String getLastFocusTag(String screenKey) {
        return lastFocusedTags.get(screenKey);
    }

    public void saveFocusPosition(String screenKey, String groupKey, int position) {
        if (position >= 0) {
            Map<String, Integer> groupMap = lastFocusedGroupPositions.get(screenKey);
            if (groupMap == null) {
                groupMap = new HashMap<>();
                lastFocusedGroupPositions.put(screenKey, groupMap);
            }
            groupMap.put(groupKey, position);
            lastFocusedGroupKeys.put(screenKey, groupKey);
        }
    }

    public int getLastFocusPosition(String screenKey, String groupKey, int defaultPosition) {
        Map<String, Integer> groupMap = lastFocusedGroupPositions.get(screenKey);
        if (groupMap != null) {
            Integer pos = groupMap.get(groupKey);
            if (pos != null) return pos;
        }
        return defaultPosition;
    }

    public void saveFocusedItemId(String screenKey, String groupKey, int itemId) {
        if (itemId != -1) {
            Map<String, Integer> groupMap = lastFocusedGroupItemIds.get(screenKey);
            if (groupMap == null) {
                groupMap = new HashMap<>();
                lastFocusedGroupItemIds.put(screenKey, groupMap);
            }
            groupMap.put(groupKey, itemId);
            lastFocusedGroupKeys.put(screenKey, groupKey);
        }
    }

    public int getLastFocusedItemId(String screenKey, String groupKey, int defaultItemId) {
        Map<String, Integer> groupMap = lastFocusedGroupItemIds.get(screenKey);
        if (groupMap != null) {
            Integer id = groupMap.get(groupKey);
            if (id != null) return id;
        }
        return defaultItemId;
    }

    public void saveFocusedItemStringId(String screenKey, String groupKey, String itemId) {
        if (itemId != null && !itemId.isEmpty()) {
            Map<String, String> groupMap = lastFocusedGroupItemStringIds.get(screenKey);
            if (groupMap == null) {
                groupMap = new HashMap<>();
                lastFocusedGroupItemStringIds.put(screenKey, groupMap);
            }
            groupMap.put(groupKey, itemId);
            lastFocusedGroupKeys.put(screenKey, groupKey);
        }
    }

    public String getLastFocusedItemStringId(String screenKey, String groupKey, String defaultItemId) {
        Map<String, String> groupMap = lastFocusedGroupItemStringIds.get(screenKey);
        if (groupMap != null) {
            String id = groupMap.get(groupKey);
            if (id != null) return id;
        }
        return defaultItemId;
    }

    public String getLastFocusedGroupKey(String screenKey) {
        return lastFocusedGroupKeys.get(screenKey);
    }

    public boolean hasSavedFocus(String screenKey) {
        return (lastFocusedViewIds.containsKey(screenKey) && lastFocusedViewIds.get(screenKey) != -1)
                || lastFocusedTags.containsKey(screenKey)
                || lastFocusedGroupKeys.containsKey(screenKey)
                || (lastFocusedGroupItemIds.containsKey(screenKey) && !lastFocusedGroupItemIds.get(screenKey).isEmpty());
    }

    public void clearFocus(String screenKey) {
        lastFocusedViewIds.remove(screenKey);
        lastFocusedTags.remove(screenKey);
        lastFocusedGroupPositions.remove(screenKey);
        lastFocusedGroupItemIds.remove(screenKey);
        lastFocusedGroupItemStringIds.remove(screenKey);
        lastFocusedGroupKeys.remove(screenKey);
    }
}
