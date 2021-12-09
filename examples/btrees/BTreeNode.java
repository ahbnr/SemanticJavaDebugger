package examples.btrees;

import java.lang.SuppressWarnings;
import java.lang.reflect.Array;

class BTreeNode<K extends Comparable<? super K>> {

    @SuppressWarnings("unchecked")
    BTreeNode(Class clazz) {
        this.keys = (K[]) Array.newInstance(clazz, 2 * BTree.order - 1);
        this.children = (BTreeNode<K>[]) Array.newInstance(this.getClass(), 2 * BTree.order);
    }

    K[] keys;
    BTreeNode<K>[] children;

    int size = 0;
}