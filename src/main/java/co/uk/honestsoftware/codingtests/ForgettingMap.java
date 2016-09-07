package co.uk.honestsoftware.codingtests;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implements a thread-safe 'forgetting map'.
 * A 'forgetting map' holds associations between a 'key' and some 'content'.
 * It holds as many associations as it can, but no more than 'x' associations
 * at a time. Associations that are least used (in a sense of 'find') are
 * removed from the map as needed.
 *
 * @param <K> Class of key
 * @param <V> Class of content
 *
 * @see {@link http://www.vogella.com/tutorials/JavaDatastructures/article.html#map}
 */
public class ForgettingMap<K, V> implements Serializable {

    private Logger logger = LogManager.getRootLogger();

    /**
     * The assumption this class is based on is that a key has only one value (content)
     * associated to it.  Otherwise we would have to handle collections of values
     * that match a single key. This assumption has been made to simplify the problem
     * although it is acknowledged that the requirement does not preclude a multi-map.
     * @param <K> key
     * @param <V> content
     */
    class MapEntry<K,V> {

        MapEntry<K,V> before;
        MapEntry<K,V> after;

        MapEntry(K key, V value, MapEntry<K,V> previous, MapEntry<K,V> next) {
            this.key = key;
            this.value = value;
            this.before = previous;
            this.after = next;
        }

        public K getKey() {
            return key;
        }

        public void setKey(K key) {
            this.key = key;
        }

        private K key;

        public V getValue() {
            return value;
        }

        public void setValue(V value) {
            this.value = value;
        }

        private V value;

    }

    private transient int maxAssociations;
    public transient MapEntry<K,V> head;
    public transient MapEntry<K,V> tail;


    public ForgettingMap(int maxAssociations) throws ForgettingException {
        if(maxAssociations > 0)
          this.maxAssociations = maxAssociations;
        else throw new ForgettingException("maxAssociations parameter must be a positive integer");
    }

    private int size;
    private static int DEFAULT_ASSOCIATIONS = 10;
    private MapEntry<K, V>[] values = new MapEntry[DEFAULT_ASSOCIATIONS];

    public int size() {
        return size;
    }

    /**
     * This find method follows the previous/next links in LRU cache
     * so that entries added or found most recently are easiest to find
     * again.  This suits browsing behaviour that returns to the same
     * entry time and again, and penalises that which ignores (forgets)
     * entries by not accessing them. This method should take O(n)
     * time to execute: if the sought key is held by tail it takes O(1)
     * constant time, but if the LRU list has to be walked then it
     * takes O(n) where n is the position in the LRU cache i.e. the
     * recentness of the key's last lookup.
     * @param key K the key
     * @return V the content
     */
    public synchronized V find(K key) {
        printLruCache("find", "BEFORE");

        MapEntry<K,V> current = tail;
        do {
            if(current == null) return null; // for first iteration if map is empty
            else if (current.getKey().equals(key)) {
                reorderLruCache(current);
                printLruCache("find", "AFTER");
                return current.getValue();
            }
            else current = current.before;
        } while(current != null);

        return null;
    }

    /**
     * Add doubles as update. An update will take O(v) time
     * where v is the number of entries in the values list,
     * to complete as it needs to walk (potentially) through the entire
     * list looking for a match. Addition however takes O(1) constant
     * time as it uses 'tail' to jump to the end of the LRU list.
     * @param key
     * @param value
     * @throws ForgettingException
     */
    public synchronized void add(K key, V value) throws ForgettingException {
        printLruCache("add", "BEFORE");
        boolean insert = true;
        for (int i = 0; i < size; i++) {
            if (values[i].getKey().equals(key)) {
                values[i].setValue(value);
                // if updating we don't reorder LRU cache
                insert = false;
            }
        }
        if (insert) {
            ensureCapa();

            // insert new associations at the end of the LRU cache
            // as indicated by 'next' being null
            MapEntry<K,V> fresh = new MapEntry<K,V>(key, value, tail, null);
            if(size > 0) // make any previous point to new entry
              tail.after = values[size++] = fresh;
            else {
                head = values[size++] = fresh;
            }
            tail = fresh;
            reorderLruCache(fresh);
        }
        printLruCache("add", "AFTER");
    }

    /**
     * Allow package-local callers (e.g. unit tests) to inspect state of map
     * without further changing LRU cache order.
     * @return MapEntry&lt;K,V&gt;[]
     */
    MapEntry<K,V>[] getValues() {
        return values;
    }

    /**
     * This method places the most recently accessed (current)
     * entry at the tail of the list. Since it uses head and tail
     * 'pointers' to place the current entry, it takes constant
     * time to complete.
     * @param current Mapentry&lt;K,V&gt;
     */
    private void reorderLruCache(MapEntry<K,V> current) {
        if (size > 1) { // nothing to do if only 1 entry in map
            MapEntry<K,V> next = current.after;

            // if null i.e. already at end of LRU cache, nothing to do
            // else ...
            if(next != null ) {
                // if only 2 entries just swap them
                if (size == 2) {
                    MapEntry<K,V> temp = current.after;
                    current = next.after;
                    next = temp.after;
                    tail = next;

                } else {
                    // reassign entry's previous to point to entry's next
                    if(current == head && current.after != null) {
                        head = current.after;
                    }
                    else {
                        current.before.after = current.after;
                    }
                    // reassign tail to point to entry
                    current.after.before = current.before;
                    tail.after = current;
                    current.before = tail;
                    tail = current;
                    current.after = null;
                }
            }
        }
    }

    /**
     * Make sure that values array has sufficient capacity
     * to add the new entry. Evict oldest (least recently accessed)
     * entry if necessary.
     */
    private void ensureCapa() {
        if(size == maxAssociations) evictLeastUsed();
        else if (size == values.length) {
            int newSize = values.length * 2;
            // check that new length doesn't go beyond max associations
            if(newSize > maxAssociations) newSize = maxAssociations;
            values = Arrays.copyOf(values, newSize);
        }
    }

    /**
     * Evict the least recently-accessed entry from the map.
     * This is determined by evicting the head entry.
     * Since it goes direct to head, it takes O(1) constant time
     * to find the entry to evict. Since it has to copy the list
     * to remove the stale entry, it takes O(v) time to do that work,
     * where v is the number of slots in the array.
     */
    private void evictLeastUsed() {
        // assign head reference to next entry in LRU cache
        K oldHeadKey = head.getKey();
        head = head.after;
        head.before = null;
        // filter values to remove stale former head which is now dangling unreferenced by LRU cache
        List<MapEntry<K,V>> list = new ArrayList<MapEntry<K,V>>();
        for(int i =0; i < values.length; i++) {
            if(values[i] != null && !values[i].getKey().equals(oldHeadKey)) {
                list.add(values[i]);
            }
        }
        values = list.toArray(new MapEntry[values.length]);
    }

    /**
     * Prints out the current LRU cache head and tail values as an aid to debugging.
     * @param methodName String
     * @param context String
     */
    private void printLruCache(String methodName, String context) {
        if(logger.isDebugEnabled()) {
            logger.debug("[" + methodName + "-" + context + "]HEAD = " + (head == null ? "[null]" : "[key: " + head.getKey() + ", " +
                    "value: " + head.getValue() + ", after: " + (head.after == null ? "null" + "]" : head.after.getKey() + "]")));
            logger.debug("[" + methodName + "-" + context + "]TAIL = " + (tail == null ? "[null]" : "[key: " + tail.getKey() +
                    ", value: " + tail.getValue() + ", before: " + (tail.before == null ? "null" + "]" : tail.before.getKey() + "]")));
        }
    }

}
