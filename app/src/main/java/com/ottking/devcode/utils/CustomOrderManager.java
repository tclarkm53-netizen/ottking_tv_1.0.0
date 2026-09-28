package com.ottking.devcode.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.ottking.devcode.db.AppDatabase;
import com.ottking.devcode.db.CategoryEntity;
import com.ottking.devcode.db.ChannelEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public class CustomOrderManager {

    private static final String PREF_NAME = "ott_king_custom_order_prefs";
    private static final String KEY_CATEGORY_ORDER = "key_category_order_json";
    private static final String KEY_HIDDEN_CATEGORIES = "key_hidden_categories_json";
    private static final String KEY_CHANNEL_ORDERS = "key_channel_orders_json"; // Map of catId -> array of channelIds
    private static final String KEY_GLOBAL_CHANNEL_ORDER = "key_global_channel_order_json";
    private static final String KEY_HIDDEN_CHANNELS = "key_hidden_channels_json";

    // Server-original orders (used when user resets ordering)
    private static final String KEY_SERVER_CATEGORY_ORDER = "key_server_category_order_json";
    private static final String KEY_SERVER_CHANNEL_ORDERS = "key_server_channel_orders_json"; // Map of catId -> array of channelIds
    private static final String KEY_SERVER_GLOBAL_CHANNEL_ORDER = "key_server_global_channel_order_json";

    public interface OnOrderChangedListener {
        void onOrderChanged();
    }

    private static volatile CustomOrderManager INSTANCE;
    private final Context appContext;
    private final SharedPreferences prefs;
    private final List<OnOrderChangedListener> listeners = new CopyOnWriteArrayList<>();

    private CustomOrderManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static CustomOrderManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (CustomOrderManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CustomOrderManager(context);
                }
            }
        }
        return INSTANCE;
    }

    public void addListener(OnOrderChangedListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(OnOrderChangedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyOrderChanged() {
        for (OnOrderChangedListener listener : listeners) {
            try {
                listener.onOrderChanged();
            } catch (Exception ignored) {}
        }
    }

    // ==========================================
    // CATEGORY ORDER & VISIBILITY
    // ==========================================

    public synchronized List<Integer> getCustomCategoryOrder() {
        List<Integer> list = new ArrayList<>();
        String json = prefs.getString(KEY_CATEGORY_ORDER, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getInt(i));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public synchronized void setCustomCategoryOrder(List<Integer> orderList) {
        try {
            JSONArray arr = new JSONArray();
            if (orderList != null) {
                for (Integer id : orderList) {
                    arr.put(id);
                }
            }
            prefs.edit().putString(KEY_CATEGORY_ORDER, arr.toString()).apply();
            syncCategoryOrdersToDbAsync(orderList);
            notifyOrderChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized Set<Integer> getHiddenCategoryIds() {
        Set<Integer> set = new HashSet<>();
        String json = prefs.getString(KEY_HIDDEN_CATEGORIES, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    set.add(arr.getInt(i));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return set;
    }

    public synchronized boolean isCategoryHidden(int categoryId) {
        return getHiddenCategoryIds().contains(categoryId);
    }

    public synchronized void setCategoryHidden(int categoryId, boolean hidden) {
        Set<Integer> set = getHiddenCategoryIds();
        if (hidden) {
            set.add(categoryId);
        } else {
            set.remove(categoryId);
        }
        try {
            JSONArray arr = new JSONArray();
            for (Integer id : set) {
                arr.put(id);
            }
            prefs.edit().putString(KEY_HIDDEN_CATEGORIES, arr.toString()).apply();
            notifyOrderChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void toggleCategoryHidden(int categoryId) {
        setCategoryHidden(categoryId, !isCategoryHidden(categoryId));
    }

    public synchronized boolean moveCategoryUp(int categoryId, List<CategoryEntity> currentCategories) {
        List<CategoryEntity> list = new ArrayList<>(currentCategories);
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == categoryId) {
                index = i;
                break;
            }
        }
        // If index is 0 or not found, or index is 1 when index 0 is "All", cannot move up further
        if (index <= 0) return false;
        if (index == 1 && isAllCategory(list.get(0))) return false;

        Collections.swap(list, index, index - 1);
        List<Integer> newOrder = new ArrayList<>();
        for (CategoryEntity c : list) {
            if (!isAllCategory(c)) {
                newOrder.add(c.id);
            }
        }
        setCustomCategoryOrder(newOrder);
        return true;
    }

    public synchronized boolean moveCategoryDown(int categoryId, List<CategoryEntity> currentCategories) {
        List<CategoryEntity> list = new ArrayList<>(currentCategories);
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == categoryId) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= list.size() - 1) return false;
        if (index == 0 && isAllCategory(list.get(0))) return false;

        Collections.swap(list, index, index + 1);
        List<Integer> newOrder = new ArrayList<>();
        for (CategoryEntity c : list) {
            if (!isAllCategory(c)) {
                newOrder.add(c.id);
            }
        }
        setCustomCategoryOrder(newOrder);
        return true;
    }

    public synchronized void resetCategoryOrder() {
        prefs.edit()
                .remove(KEY_CATEGORY_ORDER)
                .remove(KEY_HIDDEN_CATEGORIES)
                .apply();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                List<Integer> serverCatOrder = getServerCategoryOrder();
                if (!serverCatOrder.isEmpty()) {
                    for (int i = 0; i < serverCatOrder.size(); i++) {
                        db.categoryDao().updateCategoryOrder(serverCatOrder.get(i), i);
                    }
                }
            } catch (Exception ignored) {}
        });
        notifyOrderChanged();
    }

    // ==========================================
    // CHANNEL ORDER & VISIBILITY
    // ==========================================

    public synchronized List<Integer> getCustomChannelOrder(int categoryId) {
        List<Integer> list = new ArrayList<>();
        String json = prefs.getString(KEY_CHANNEL_ORDERS, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(json);
                String key = String.valueOf(categoryId);
                if (obj.has(key)) {
                    JSONArray arr = obj.getJSONArray(key);
                    for (int i = 0; i < arr.length(); i++) {
                        list.add(arr.getInt(i));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public synchronized void setCustomChannelOrder(int categoryId, List<Integer> orderList) {
        try {
            JSONObject obj;
            String json = prefs.getString(KEY_CHANNEL_ORDERS, null);
            if (json != null && !json.isEmpty()) {
                obj = new JSONObject(json);
            } else {
                obj = new JSONObject();
            }
            JSONArray arr = new JSONArray();
            if (orderList != null) {
                for (Integer id : orderList) {
                    arr.put(id);
                }
            }
            obj.put(String.valueOf(categoryId), arr);
            prefs.edit().putString(KEY_CHANNEL_ORDERS, obj.toString()).apply();
            syncChannelOrdersToDbAsync();
            notifyOrderChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized Set<Integer> getHiddenChannelIds() {
        Set<Integer> set = new HashSet<>();
        String json = prefs.getString(KEY_HIDDEN_CHANNELS, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    set.add(arr.getInt(i));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return set;
    }

    public synchronized boolean isChannelHidden(int channelId) {
        return getHiddenChannelIds().contains(channelId);
    }

    public synchronized void setChannelHidden(int channelId, boolean hidden) {
        Set<Integer> set = getHiddenChannelIds();
        if (hidden) {
            set.add(channelId);
        } else {
            set.remove(channelId);
        }
        try {
            JSONArray arr = new JSONArray();
            for (Integer id : set) {
                arr.put(id);
            }
            prefs.edit().putString(KEY_HIDDEN_CHANNELS, arr.toString()).apply();
            notifyOrderChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void toggleChannelHidden(int channelId) {
        setChannelHidden(channelId, !isChannelHidden(channelId));
    }

    public synchronized boolean moveChannelUp(int categoryId, int channelId, List<ChannelEntity> currentChannels) {
        List<ChannelEntity> list = new ArrayList<>(currentChannels);
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == channelId) {
                index = i;
                break;
            }
        }
        if (index <= 0) return false;
        Collections.swap(list, index, index - 1);
        List<Integer> newOrder = new ArrayList<>();
        for (ChannelEntity ch : list) {
            newOrder.add(ch.id);
        }
        setCustomChannelOrder(categoryId, newOrder);
        return true;
    }

    public synchronized boolean moveChannelDown(int categoryId, int channelId, List<ChannelEntity> currentChannels) {
        List<ChannelEntity> list = new ArrayList<>(currentChannels);
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == channelId) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= list.size() - 1) return false;
        Collections.swap(list, index, index + 1);
        List<Integer> newOrder = new ArrayList<>();
        for (ChannelEntity ch : list) {
            newOrder.add(ch.id);
        }
        setCustomChannelOrder(categoryId, newOrder);
        return true;
    }

    public synchronized boolean moveChannelToTop(int categoryId, int channelId, List<ChannelEntity> currentChannels) {
        List<ChannelEntity> list = new ArrayList<>(currentChannels);
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == channelId) {
                index = i;
                break;
            }
        }
        if (index <= 0) return false;
        ChannelEntity item = list.remove(index);
        list.add(0, item);
        List<Integer> newOrder = new ArrayList<>();
        for (ChannelEntity ch : list) {
            newOrder.add(ch.id);
        }
        setCustomChannelOrder(categoryId, newOrder);
        return true;
    }

    public synchronized void resetChannelOrderForCategory(int categoryId) {
        try {
            String json = prefs.getString(KEY_CHANNEL_ORDERS, null);
            if (json != null && !json.isEmpty()) {
                JSONObject obj = new JSONObject(json);
                obj.remove(String.valueOf(categoryId));
                prefs.edit().putString(KEY_CHANNEL_ORDERS, obj.toString()).apply();
            }
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(appContext);
                    List<Integer> serverChanOrder = getServerChannelOrder(categoryId);
                    if (!serverChanOrder.isEmpty()) {
                        for (int i = 0; i < serverChanOrder.size(); i++) {
                            db.channelDao().updateChannelOrder(serverChanOrder.get(i), i);
                        }
                    }
                } catch (Exception ignored) {}
            });
            notifyOrderChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void resetAllOrders() {
        prefs.edit()
                .remove(KEY_CATEGORY_ORDER)
                .remove(KEY_HIDDEN_CATEGORIES)
                .remove(KEY_CHANNEL_ORDERS)
                .remove(KEY_GLOBAL_CHANNEL_ORDER)
                .remove(KEY_HIDDEN_CHANNELS)
                .apply();
        restoreServerOrdersInDb();
        notifyOrderChanged();
    }

    // ==========================================
    // SERVER ORDER PERSISTENCE & RESTORATION
    // ==========================================

    public synchronized void saveServerOriginalOrders(List<CategoryEntity> catEntities, List<ChannelEntity> chanEntities) {
        try {
            if (catEntities != null && !catEntities.isEmpty()) {
                JSONArray catArr = new JSONArray();
                for (CategoryEntity c : catEntities) {
                    if (c != null && !isAllCategory(c)) {
                        catArr.put(c.id);
                    }
                }
                prefs.edit().putString(KEY_SERVER_CATEGORY_ORDER, catArr.toString()).apply();
            }

            if (chanEntities != null && !chanEntities.isEmpty()) {
                JSONObject chanMap = new JSONObject();
                JSONArray globalChanArr = new JSONArray();
                Map<Integer, JSONArray> catToChans = new HashMap<>();

                for (ChannelEntity ch : chanEntities) {
                    if (ch == null) continue;
                    globalChanArr.put(ch.id);
                    if (!catToChans.containsKey(ch.categoryId)) {
                        JSONArray a = new JSONArray();
                        catToChans.put(ch.categoryId, a);
                        chanMap.put(String.valueOf(ch.categoryId), a);
                    }
                    catToChans.get(ch.categoryId).put(ch.id);
                }

                prefs.edit()
                        .putString(KEY_SERVER_CHANNEL_ORDERS, chanMap.toString())
                        .putString(KEY_SERVER_GLOBAL_CHANNEL_ORDER, globalChanArr.toString())
                        .apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized List<Integer> getServerCategoryOrder() {
        List<Integer> list = new ArrayList<>();
        String json = prefs.getString(KEY_SERVER_CATEGORY_ORDER, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getInt(i));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public synchronized List<Integer> getServerChannelOrder(int categoryId) {
        List<Integer> list = new ArrayList<>();
        String json = prefs.getString(KEY_SERVER_CHANNEL_ORDERS, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(json);
                String key = String.valueOf(categoryId);
                if (obj.has(key)) {
                    JSONArray arr = obj.getJSONArray(key);
                    for (int i = 0; i < arr.length(); i++) {
                        list.add(arr.getInt(i));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public synchronized List<Integer> getServerGlobalChannelOrder() {
        List<Integer> list = new ArrayList<>();
        String json = prefs.getString(KEY_SERVER_GLOBAL_CHANNEL_ORDER, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getInt(i));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public void restoreServerOrdersInDb() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                List<Integer> serverCatOrder = getServerCategoryOrder();
                if (!serverCatOrder.isEmpty()) {
                    for (int i = 0; i < serverCatOrder.size(); i++) {
                        db.categoryDao().updateCategoryOrder(serverCatOrder.get(i), i);
                    }
                }
                List<Integer> serverGlobalChanOrder = getServerGlobalChannelOrder();
                if (!serverGlobalChanOrder.isEmpty()) {
                    for (int i = 0; i < serverGlobalChanOrder.size(); i++) {
                        db.channelDao().updateChannelOrder(serverGlobalChanOrder.get(i), i);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ==========================================
    // ORDER CALCULATION (CRITICAL SPEC LOGIC)
    // ==========================================

    /**
     * Sorts the category list respecting:
     * 1. "All" category at top.
     * 2. User's custom category order for other categories (or server order when reset).
     * 3. Filter out hidden categories if includeHidden == false.
     */
    public List<CategoryEntity> buildOrderedCategories(List<CategoryEntity> rawCategories, boolean includeHidden) {
        List<CategoryEntity> result = new ArrayList<>();
        if (rawCategories == null || rawCategories.isEmpty()) {
            return result;
        }

        CategoryEntity allCat = null;
        List<CategoryEntity> nonAllCats = new ArrayList<>();
        for (CategoryEntity c : rawCategories) {
            if (isAllCategory(c)) {
                if (allCat == null) allCat = c;
            } else {
                if (includeHidden || !isCategoryHidden(c.id)) {
                    nonAllCats.add(c);
                }
            }
        }

        List<Integer> customOrder = getCustomCategoryOrder();
        if (!customOrder.isEmpty()) {
            Map<Integer, Integer> orderMap = new HashMap<>();
            for (int i = 0; i < customOrder.size(); i++) {
                orderMap.put(customOrder.get(i), i);
            }
            Collections.sort(nonAllCats, (a, b) -> {
                int orderA = orderMap.containsKey(a.id) ? orderMap.get(a.id) : (100000 + a.order);
                int orderB = orderMap.containsKey(b.id) ? orderMap.get(b.id) : (100000 + b.order);
                if (orderA != orderB) {
                    return Integer.compare(orderA, orderB);
                }
                return Integer.compare(a.id, b.id);
            });
        } else {
            List<Integer> serverOrder = getServerCategoryOrder();
            if (!serverOrder.isEmpty()) {
                Map<Integer, Integer> serverMap = new HashMap<>();
                for (int i = 0; i < serverOrder.size(); i++) {
                    serverMap.put(serverOrder.get(i), i);
                }
                Collections.sort(nonAllCats, (a, b) -> {
                    int orderA = serverMap.containsKey(a.id) ? serverMap.get(a.id) : (100000 + a.order);
                    int orderB = serverMap.containsKey(b.id) ? serverMap.get(b.id) : (100000 + b.order);
                    if (orderA != orderB) {
                        return Integer.compare(orderA, orderB);
                    }
                    return Integer.compare(a.id, b.id);
                });
            } else {
                Collections.sort(nonAllCats, (a, b) -> {
                    if (a.order > 0 && b.order > 0) {
                        if (a.order != b.order) {
                            return Integer.compare(a.order, b.order);
                        }
                        return Integer.compare(a.id, b.id);
                    }
                    if (a.order > 0 && b.order <= 0) return -1;
                    if (b.order > 0 && a.order <= 0) return 1;
                    return Integer.compare(a.id, b.id);
                });
            }
        }

        if (allCat != null) {
            result.add(allCat);
        }
        result.addAll(nonAllCats);
        return result;
    }

    public List<CategoryEntity> buildOrderedCategories(List<CategoryEntity> rawCategories) {
        return buildOrderedCategories(rawCategories, false);
    }

    /**
     * Sorts channels of a specific category according to user's custom channel order (or server order when reset).
     * If category itself is hidden and includeHidden is false, returns an empty list.
     */
    public List<ChannelEntity> buildOrderedChannelsForCategory(int categoryId, List<ChannelEntity> categoryChannels, boolean includeHidden) {
        List<ChannelEntity> result = new ArrayList<>();
        if (categoryChannels == null || categoryChannels.isEmpty()) {
            return result;
        }

        // If category is hidden and includeHidden is false, channels must be hidden!
        if (!includeHidden && isCategoryHidden(categoryId)) {
            return result;
        }

        for (ChannelEntity ch : categoryChannels) {
            if (includeHidden || (!isChannelHidden(ch.id) && !isCategoryHidden(ch.categoryId))) {
                result.add(ch);
            }
        }

        List<Integer> customOrder = getCustomChannelOrder(categoryId);
        if (!customOrder.isEmpty()) {
            Map<Integer, Integer> orderMap = new HashMap<>();
            for (int i = 0; i < customOrder.size(); i++) {
                orderMap.put(customOrder.get(i), i);
            }
            Collections.sort(result, (a, b) -> {
                int orderA = orderMap.containsKey(a.id) ? orderMap.get(a.id) : (100000 + a.id);
                int orderB = orderMap.containsKey(b.id) ? orderMap.get(b.id) : (100000 + b.id);
                if (orderA != orderB) {
                    return Integer.compare(orderA, orderB);
                }
                return Integer.compare(a.id, b.id);
            });
        } else {
            List<Integer> serverOrder = getServerChannelOrder(categoryId);
            if (!serverOrder.isEmpty()) {
                Map<Integer, Integer> serverMap = new HashMap<>();
                for (int i = 0; i < serverOrder.size(); i++) {
                    serverMap.put(serverOrder.get(i), i);
                }
                Collections.sort(result, (a, b) -> {
                    int orderA = serverMap.containsKey(a.id) ? serverMap.get(a.id) : (100000 + a.id);
                    int orderB = serverMap.containsKey(b.id) ? serverMap.get(b.id) : (100000 + b.id);
                    if (orderA != orderB) {
                        return Integer.compare(orderA, orderB);
                    }
                    return Integer.compare(a.id, b.id);
                });
            } else {
                // Sort by default entity order: positive sort_order first, then by ID ASC
                Collections.sort(result, (a, b) -> {
                    if (a.order > 0 && b.order > 0) {
                        if (a.order != b.order) {
                            return Integer.compare(a.order, b.order);
                        }
                        return Integer.compare(a.id, b.id);
                    }
                    if (a.order > 0 && b.order <= 0) return -1;
                    if (b.order > 0 && a.order <= 0) return 1;
                    return Integer.compare(a.id, b.id);
                });
            }
        }

        return result;
    }

    public List<ChannelEntity> buildOrderedChannelsForCategory(int categoryId, List<ChannelEntity> categoryChannels) {
        return buildOrderedChannelsForCategory(categoryId, categoryChannels, false);
    }

    /**
     * Builds the unified All Channels and Player Channels order:
     * Iterates categories in their customized category order:
     * For each category:
     *   Takes its channels, sorted by their customized channel order.
     * Appends any orphan channels at the end.
     * Strictly hides channels that are hidden OR belong to hidden categories when includeHidden == false.
     */
    public List<ChannelEntity> buildOrderedAllChannels(List<ChannelEntity> rawChannels, List<CategoryEntity> rawCategories, boolean includeHidden) {
        List<ChannelEntity> result = new ArrayList<>();
        if (rawChannels == null || rawChannels.isEmpty()) {
            return result;
        }

        // 1. Get ordered categories (excluding "All")
        List<CategoryEntity> orderedCats = buildOrderedCategories(rawCategories, includeHidden);

        // Group channels by categoryId
        Map<Integer, List<ChannelEntity>> catChannelsMap = new HashMap<>();
        for (ChannelEntity ch : rawChannels) {
            if (!includeHidden && (isChannelHidden(ch.id) || isCategoryHidden(ch.categoryId))) {
                continue;
            }
            if (!catChannelsMap.containsKey(ch.categoryId)) {
                catChannelsMap.put(ch.categoryId, new ArrayList<>());
            }
            catChannelsMap.get(ch.categoryId).add(ch);
        }

        Set<Integer> processedChannelIds = new HashSet<>();

        // 2. Add channels in order of categories -> in order of channels inside each category
        for (CategoryEntity cat : orderedCats) {
            if (isAllCategory(cat)) continue;
            if (!includeHidden && isCategoryHidden(cat.id)) continue;

            List<ChannelEntity> catChannels = catChannelsMap.get(cat.id);
            if (catChannels != null && !catChannels.isEmpty()) {
                List<ChannelEntity> sortedCatChannels = buildOrderedChannelsForCategory(cat.id, catChannels, includeHidden);
                for (ChannelEntity ch : sortedCatChannels) {
                    if (!processedChannelIds.contains(ch.id)) {
                        result.add(ch);
                        processedChannelIds.add(ch.id);
                    }
                }
            }
        }

        // 3. Any channels whose category was not in categories list (orphan channels only):
        for (ChannelEntity ch : rawChannels) {
            if (!processedChannelIds.contains(ch.id)) {
                if (!includeHidden && (isChannelHidden(ch.id) || isCategoryHidden(ch.categoryId))) {
                    continue;
                }
                result.add(ch);
                processedChannelIds.add(ch.id);
            }
        }

        // 4. If user also defined a specific "All Channels" custom channel ordering, apply it
        int allCatId = -1;
        for (CategoryEntity c : rawCategories) {
            if (isAllCategory(c)) {
                allCatId = c.id;
                break;
            }
        }
        List<Integer> allCatOrder = getCustomChannelOrder(allCatId);
        if (allCatId != -1 && allCatOrder.isEmpty()) {
            allCatOrder = getCustomChannelOrder(-1);
        }
        if (!allCatOrder.isEmpty()) {
            Map<Integer, Integer> allOrderMap = new HashMap<>();
            for (int i = 0; i < allCatOrder.size(); i++) {
                allOrderMap.put(allCatOrder.get(i), i);
            }
            Collections.sort(result, (a, b) -> {
                boolean hasA = allOrderMap.containsKey(a.id);
                boolean hasB = allOrderMap.containsKey(b.id);
                if (hasA && hasB) {
                    return Integer.compare(allOrderMap.get(a.id), allOrderMap.get(b.id));
                } else if (hasA) {
                    return -1;
                } else if (hasB) {
                    return 1;
                }
                return 0; // maintain category-grouped order
            });
        }

        return result;
    }

    public List<ChannelEntity> buildOrderedAllChannels(List<ChannelEntity> rawChannels, List<CategoryEntity> rawCategories) {
        return buildOrderedAllChannels(rawChannels, rawCategories, false);
    }

    public static boolean isAllCategory(CategoryEntity c) {
        if (c == null) return false;
        if (c.id == -1) return true;
        String name = c.name != null ? c.name.trim() : "";
        return "all".equalsIgnoreCase(name) || "all channels".equalsIgnoreCase(name)
                || "সকল চ্যানেল".equalsIgnoreCase(name) || "সকল".equalsIgnoreCase(name);
    }

    // ==========================================
    // ENTITY ORDER PRESERVATION (FOR API CLIENT)
    // ==========================================

    public void applyCustomOrdersToEntities(List<CategoryEntity> catEntities, List<ChannelEntity> chanEntities) {
        if (catEntities != null) {
            List<Integer> customCatOrder = getCustomCategoryOrder();
            if (!customCatOrder.isEmpty()) {
                Map<Integer, Integer> catOrderMap = new HashMap<>();
                for (int i = 0; i < customCatOrder.size(); i++) {
                    catOrderMap.put(customCatOrder.get(i), i);
                }
                for (CategoryEntity cat : catEntities) {
                    if (catOrderMap.containsKey(cat.id)) {
                        cat.order = catOrderMap.get(cat.id);
                    }
                }
            }
        }

        if (chanEntities != null && !chanEntities.isEmpty() && catEntities != null) {
            List<ChannelEntity> ordered = buildOrderedAllChannels(chanEntities, catEntities, true);
            for (int i = 0; i < ordered.size(); i++) {
                ordered.get(i).order = i;
            }
            chanEntities.clear();
            chanEntities.addAll(ordered);
        }
    }

    private void syncCategoryOrdersToDbAsync(List<Integer> orderList) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                for (int i = 0; i < orderList.size(); i++) {
                    db.categoryDao().updateCategoryOrder(orderList.get(i), i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void syncChannelOrdersToDbAsync() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                List<CategoryEntity> cats = db.categoryDao().getAllCategoriesSync();
                List<ChannelEntity> chans = db.channelDao().getAllChannelsSync();
                List<ChannelEntity> ordered = buildOrderedAllChannels(chans, cats, true);
                for (int i = 0; i < ordered.size(); i++) {
                    db.channelDao().updateChannelOrder(ordered.get(i).id, i);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
