package examples.btrees.tests;

import java.lang.String;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import examples.btrees.BTree;

public class InsertTest {
    private static <K extends Comparable<? super K>> List<K> treeToList(BTree<K> tree) {
        var iterator = tree.iterator();

        var list = new ArrayList<K>();
        iterator.forEachRemaining(list::add);

        return list;
    }

    private static <K extends Comparable<? super K>> void assertEquals(String message, List<K> expected, BTree<K> actual) {
        var treeList = treeToList(actual);

        if (expected.size() != treeList.size()) {
            throw new RuntimeException(message + "\nDiffering sizes: Expected " + expected.size() + ". Actual: " + treeList.size() + ".\nFull Comparison: Expected: " + expected.toString() + " Actual: " + treeList.toString());
        }

        for (int i = 0; i < expected.size(); ++i) {
            if (expected.get(i).compareTo(treeList.get(i)) != 0) {
                throw new RuntimeException(message + "\nDiffering entry at index " + i + ".\nFull Comparison: Expected: " + expected.toString() + " Actual: " + treeList.toString());
            }
        }
    }

    private static void testSingleInsert() {
        var bTree = new BTree<Integer>();
        bTree.insert(42);

        assertEquals("Single Insert", Arrays.asList(42), bTree);

        System.out.println("PASSED.");
    }

    private static void testMultiInsert() {
        var bTree = new BTree<Integer>();
        bTree.insert(4);
        bTree.insert(1);
        bTree.insert(3);
        bTree.insert(2);
        bTree.insert(5);

        assertEquals("Multi Insert", Arrays.asList(1, 2, 3, 4, 5), bTree);

        System.out.println("PASSED.");
    }

    public static void main(String[] args) {
        testSingleInsert();
        testMultiInsert();
    }
}