package casestudies.btrees.iterator;

import java.lang.String;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import casestudies.btrees.iterator.BTree;
import casestudies.btrees.iterator.BTreeIterator;

public class IteratorTest {
    private static <K extends Comparable<? super K>> void assertEquals(
            List<K> expected,
            List<K> actual
    ) {
        if (expected.size() != actual.size()) {
            throw new RuntimeException("Differing sizes: Expected " + expected.size() + ". Actual: " + actual.size() + ".\nFull Comparison: Expected: " + expected.toString() + " Actual: " + actual.toString());
        }

        for (int i = 0; i < expected.size(); ++i) {
            if (expected.get(i).compareTo(actual.get(i)) != 0) {
                throw new RuntimeException("Differing entry at index " + i + ".\nFull Comparison: Expected: " + expected.toString() + " Actual: " + actual.toString());
            }
        }
    }

    public static void main(String[] args) {
        var tree = new BTree<Integer>();
        tree.insert(7);
        tree.insert(4);
        tree.insert(1);
        tree.insert(3);
        tree.insert(2);
        tree.insert(5);
        tree.insert(6);

        var iterator = tree.iterator();
        var keyList = new ArrayList<Integer>();
        iterator.forEachRemaining(keyList::add);

        assertEquals(
                Arrays.asList(1, 2, 3, 4, 5, 6, 7),
                keyList
        );

        System.out.println("PASSED");
    }
}