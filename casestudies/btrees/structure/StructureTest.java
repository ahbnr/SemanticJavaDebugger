package casestudies.btrees.structure;

import java.lang.String;
import java.util.Arrays;

import casestudies.btrees.structure.BTree;

public class StructureTest {
    public static void main(String[] args) {
        var tree = new BTree<Integer>();

        for (var i : Arrays.asList(0, 1, 2, 3)) tree.insert(i);

        System.out.println("Test Point");

        for (var i : tree) System.out.println(i);
    }
}