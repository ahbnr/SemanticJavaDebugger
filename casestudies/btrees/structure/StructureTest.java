package casestudies.btrees.structure;

import java.lang.String;
import java.util.Arrays;

import casestudies.btrees.structure.BTree;

public class StructureTest {
    public static void main(String[] args) {
        var tree = new BTree<Integer>();

        var expected = Arrays.asList(0, 1, 2, 3);
        for (var e : expected) {
            tree.insert(e);
        }

        System.out.println("Test Point");
    }
}