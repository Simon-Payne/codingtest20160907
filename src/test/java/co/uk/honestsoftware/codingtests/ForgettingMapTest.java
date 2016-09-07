package co.uk.honestsoftware.codingtests;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for @{ForgettingMap}.
 * @see {@link http://tutorials.jenkov.com/java-util-concurrent/countdownlatch.html}
 */
public class ForgettingMapTest {

    private Logger logger = LogManager.getRootLogger();

    //****************************************
    // CONSTRUCTOR TESTS
    //****************************************
    @Test(expected = ForgettingException.class)
    public void testConstructZero() throws ForgettingException
    {
        new ForgettingMap<String, String>(0);
    }

    @Test(expected = ForgettingException.class)
    public void testConstructMinusOne() throws ForgettingException
    {
        new ForgettingMap<String, String>(-1);
    }

    @Test
    public void testConstructOne() throws ForgettingException
    {
        new ForgettingMap<String, String>(1);
    }

    //****************************************
    // ADD TESTS
    //****************************************

    /**
     * It should be possible to add a null (key) -> null  (content) association.
     */
    @Test
    public void testAddNullNullAssociation() throws ForgettingException
    {
        ForgettingMap<String, String> map = new ForgettingMap<String,String>(10);
        map.add(null, null);
        assertThat(map.size(), equalTo(1));
    }

    /**
     * It should be possible to add a null (key) -> non-null (content) association.
     */
    @Test
    public void testAddNullNonNullAssociation() throws ForgettingException
    {
        ForgettingMap<String, String> map = new ForgettingMap<String,String>(10);
        map.add(null, "something");
        assertThat(map.size(), equalTo(1));
    }

    /**
     * It should be possible to add a non-null (key) -> null (content) association.
     */
    @Test
    public void testAddNonNullNullAssociation() throws ForgettingException
    {
        ForgettingMap<String, String> map = new ForgettingMap<String,String>(10);
        map.add("something", null);
        assertThat(map.size(), equalTo(1));
    }

    /**
     * It should be possible to add a non-null (key) -> non-null (content) association.
     */
    @Test
    public void testAddNonNullNonNullAssociation() throws ForgettingException
    {
        ForgettingMap<String, String> map = new ForgettingMap<String,String>(10);
        map.add("something", "something");
        assertThat(map.size(), equalTo(1));
    }

    /**
     * It should be possible to update an already-present key with a new association.
     */
    @Test
    public void testReAddAssociation() throws ForgettingException
    {
        ForgettingMap<String, String> map = new ForgettingMap<String,String>(10);
        map.add("something", "something");
        map.add("something", "another thing");
        assertThat(map.size(), equalTo(1));
        assertThat(map.find("something"), equalTo("another thing"));
    }

    //****************************************
    // FIND TESTS
    //****************************************

    /**
     * A find of a non-existent association should return null.
     */
    @Test
    public void testFindNonExistentAssociation() throws ForgettingException
    {
        ForgettingMap<String, String> map = new ForgettingMap<String,String>(10);
        assertNull(map.find("something"));
    }

    /**
     * A find of an extant association should return the content associated with it.
     */
    @Test
    public void testFindExtantAssociation() throws ForgettingException
    {
        ForgettingMap<String, String> map = new ForgettingMap<String,String>(10);
        map.add("something", "something");
        assertThat(map.find("something"), equalTo("something"));
    }

    /**
     * Add of several associations should order the least-used list so that the key
     * is subsequently located at the end of it. i.e.
     * <pre>
     *     a -> b -> c
     * <pre>
     */
    @Test
    public void testAddAssociationsCorrectlyOrdersLRUList() throws ForgettingException
    {
        ForgettingMap<String, Integer> map = new ForgettingMap<String,Integer>(10);
        map.add("a", 1);
        map.add("b", 2);
        map.add("c", 3);
        ForgettingMap<String,Integer>.MapEntry<String,Integer> tail = map.tail;
        assertThat(tail.getKey(), equalTo("c"));
        assertThat(tail.getValue(), equalTo(3));
    }

    /**
     * A find of an extant association should reorder the least-used list so that the key
     * is subsequently located at the end of it. i.e.
     * <pre>
     *     b -> c -> a
     * </pre>
     */
    @Test
    public void testFindExtantAssociationReordersLRUList() throws ForgettingException
    {
        ForgettingMap<String, Integer> map = new ForgettingMap<String,Integer>(10);
        map.add("a", 1);
        map.add("b", 2);
        map.add("c", 3);
        assertThat(map.find("a"), equalTo(1));
        ForgettingMap<String,Integer>.MapEntry<String,Integer> tail = map.tail;
        assertThat(tail.getKey(), equalTo("a"));
        assertThat(tail.getValue(), equalTo(1));
    }

    /**
     * When more than max associations are added to map, assuming no finds, head entry
     * in LRU cache is evicted to make space.
     */
    @Test
    public void testMaxAssociationsEvictsForgottenEntry() throws ForgettingException
    {
        ForgettingMap<String, Integer> map = new ForgettingMap<String,Integer>(5);
        map.add("a", 1);
        map.add("b", 2);
        map.add("c", 3);
        map.add("d", 4);
        map.add("e", 5);
        map.add("f", 6);
        assertThat(map.head.getKey(), equalTo("b"));
        assertThat(map.head.getValue(), equalTo(2));
        assertThat(map.tail.getKey(), equalTo("f"));
        assertThat(map.tail.getValue(), equalTo(6));
    }

    /**
     * When more than max associations are added to map, given a find is performed,
     * on next add the head entry in LRU cache is evicted to make space. i.e.
     * <pre>
     * before: a->1, b->2, c->3, d->4, e->5, f->6
     * Find 'a' moves 'a' to tail of LRU cache
     * after (find): before: b->2, c->3, d->4, e->5, a->1
     * Add 'f' evicts 'b'
     * after (add): before: c->3, d->4, e->5, f->6, a->1
     * </pre>
     */
    @Test
    public void testMaxAssociationsAndFindsEvictsForgottenEntry() throws ForgettingException
    {
        ForgettingMap<String, Integer> map = new ForgettingMap<String,Integer>(5);
        map.add("a", 1);
        map.add("b", 2);
        map.add("c", 3);
        map.add("d", 4);
        map.add("e", 5);
        map.find("a");
        map.add("f", 6);
        assertThat(map.head.getKey(), equalTo("c"));
        assertThat(map.head.getValue(), equalTo(3));
        assertThat(map.tail.getKey(), equalTo("f"));
        assertThat(map.tail.getValue(), equalTo(6));
    }

    /**
     * Find should be thread-safe.
     */
    @Test
    public void testFindThreadSafe() throws ForgettingException, InterruptedException
    {
        ForgettingMap<Long, String> map = new ForgettingMap<Long, String>(3);
        map.add(1L, "apple");
        map.add(2L, "banana");
        map.add(3L, "cherry");
        CountDownLatch latch = new CountDownLatch(2); // wait for two invocations
        Waiter waiter = new Waiter(map, latch, new Long[]{2L}, new Long[]{1L});
        TaskRunner finder = new TaskRunner<Long, String>(1, latch, map, TaskType.FIND, 2L, null);
        TaskRunner adder = new TaskRunner<Long, String>(2, latch, map, TaskType.ADD, 4L, "durian");
        new Thread(waiter).start();
        new Thread(finder).start();
        new Thread(adder).start();
    }

    /**
     * Add should be thread-safe.
     */
    @Test
    public void testAddThreadSafe() throws ForgettingException, InterruptedException
    {
        ForgettingMap<Long, String> map = new ForgettingMap<Long, String>(2);
        CountDownLatch latch = new CountDownLatch(2); // wait for two invocations
        Waiter waiter = new Waiter(map, latch, new Long[]{1L, 2L}, new Long[0]);
        TaskRunner adder1 = new TaskRunner<Long, String>(1, latch, map, TaskType.ADD, 1L, "apple");
        TaskRunner adder2 = new TaskRunner<Long, String>(2, latch, map, TaskType.ADD, 2L, "banana");
        new Thread(waiter).start();
        new Thread(adder1).start();
        new Thread(adder2).start();
    }



    //***********************************************
    // PRIVATE TEST FIXTURES
    //***********************************************

    /**
     * Waits for the TaskRunners to complete before checking
     * the final state of the map against expected values.
     */
    class Waiter implements Runnable{

        ForgettingMap<Long, String> map;
        CountDownLatch latch = null;
        Long[] idsToFind;
        Long[] idsNotToFind;

        public Waiter(ForgettingMap<Long, String> map, CountDownLatch latch, Long[] idsToFind, Long[] idsNotToFind) {
            this.map = map;
            this.latch = latch;
            this.idsToFind = idsToFind;
            this.idsNotToFind = idsNotToFind;
        }

        public void run() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            logger.debug("Waiter Released");
            // list keys currently in map
            List<Long> ids = new ArrayList<Long>();
            ForgettingMap<Long,String>.MapEntry<Long,String>[] values = map.getValues();
            for(int v = 0; v < values.length; v++) {
                if(values[v] != null)
                  ids.add(values[v].getKey());
            }
            // check those that should be found...
            for (int i = 0; i < idsToFind.length; i++) {
                assertTrue(ids.contains(idsToFind[i]));
            }

            // ...or not as the case may be
            for (int i = 0; i < idsNotToFind.length; i++) {
                assertFalse(ids.contains(idsNotToFind[i]));
            }
        }
    }

    /**
     * Enumeration of types of task that a TaskRunner may perform.
     */
    enum TaskType {
        ADD,
        FIND
    }

    /**
     * Runs a map task: either add() or find(). This method
     * uses a CountDown latch from the java.util.concurrent library
     * as this handles multi-threading complexity for us.
     * @param <K> key
     * @param <V> content
     */
    class TaskRunner<K,V> implements Runnable {

        private int id;
        private CountDownLatch latch;
        private ForgettingMap<K,V> map;
        private TaskType taskType;
        private K key;
        private V value;

        TaskRunner(int id, CountDownLatch latch, ForgettingMap<K,V> map, TaskType taskType, K key, V value) {
            this.id = id;
            this.latch = latch;
            this.map = map;
            this.taskType = taskType;
            this.key = key;
            this.value = value;
        }

        @Override
        public void run() {
            logger.debug("Running thread " + id);
            try {
                if(taskType.equals(TaskType.ADD)) map.add(key, value);
                else if (taskType.equals(TaskType.FIND)) map.find(key);
            } catch (ForgettingException fe) {
                throw new RuntimeException(fe);
            }
            logger.debug("Completed thread " + id);
            latch.countDown();
        }
    }

}
