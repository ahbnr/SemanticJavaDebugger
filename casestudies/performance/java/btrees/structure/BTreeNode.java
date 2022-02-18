package btrees.structure;

import java.lang.SuppressWarnings;
import java.lang.reflect.Array;

// Everything is public to make tests easier
public class BTreeNode<K extends Comparable<? super K>> {

    @SuppressWarnings("unchecked")
    public BTreeNode(Class clazz) {
        this.keys = (K[]) Array.newInstance(clazz, 2 * BTree.order - 1);
        this.children = (BTreeNode<K>[]) Array.newInstance(this.getClass(), 2 * BTree.order);
    }

    public K[] keys;
    public BTreeNode<K>[] children;

    // number of keys, number of children is this size + 1
    public int size = 0;
}