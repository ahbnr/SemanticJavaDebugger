package casestudies.btrees.structure;

import java.lang.SuppressWarnings;
import java.lang.String;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Array;

import casestudies.btrees.structure.BTree;
import casestudies.btrees.structure.BTreeNode;

public class StructureTest {
    private static BTreeNode<Integer> newNode() {
        return new BTreeNode<Integer>(Integer.class);
    }

    @SuppressWarnings("unchecked")
    private static BTreeNode<Integer>[] newFilledNodeArray(int size) {
        var array = (BTreeNode<Integer>[]) Array.newInstance(BTreeNode.class, size);
        for (int i = 0; i < array.length; ++i) {
            array[i] = newNode();
        }

        return array;
    }

    public static void main(String[] args) {
        var root = newNode();
        root.children[0] = newNode();

        var child = root.children[0];
        child.children[1] = newNode();

        var grandChild = child.children[1];
        grandChild.children = newFilledNodeArray(2 * BTree.order + 1);

        System.out.println("Test point: Node Conditions");
    }
}