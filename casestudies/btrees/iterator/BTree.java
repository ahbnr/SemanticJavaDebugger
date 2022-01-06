package casestudies.btrees.iterator;

import java.lang.Iterable;
import java.util.Iterator;

/**
 * Based on Cormen et al., Introduction to Algorithms, Third Edition. Chapter 18
 */
public class BTree<K extends Comparable<? super K>> implements Iterable<K> {
    public static int order = 2;

    private BTreeNode<K> root = null;

    public Iterator<K> iterator() {
        return new BTreeIterator<K>(root);
    }

    public void insert(K key) {
        if (root == null) {
            root = new BTreeNode<K>(key.getClass());
            root.keys[0] = key;
            root.size = 1;
        } else {
            if (root.size == 2 * order - 1) {
                var newNode = new BTreeNode<K>(key.getClass());
                newNode.children[0] = root;
                root = newNode;

                split(key.getClass(), root, 0);
            }

            insertNonFull(root, key);
        }
    }

    private void insertNonFull(BTreeNode<K> node, K toInsert) {
        boolean isLeaf = node.children[0] == null;

        int i = node.size - 1;
        if (isLeaf) {
            while (i >= 0 && toInsert.compareTo(node.keys[i]) < 0) {
                node.keys[i + 1] = node.keys[i];
                --i;
            }

            node.keys[i + 1] = toInsert;
            ++node.size;
        } else {
            // couldnt this be done with a binary search?
            while (i >= 0 && toInsert.compareTo(node.keys[i]) < 0) {
                --i;
            }

            var targetChild = node.children[i + 1];
            if (targetChild.size == 2 * order - 1) {
                split(toInsert.getClass(), node, i + 1);
            }

            insertNonFull(targetChild, toInsert);
        }
    }

    private void split(Class keyClass, BTreeNode<K> parent, int fullChildIdx) {
        var newNode = new BTreeNode<K>(keyClass);
        var toSplit = parent.children[fullChildIdx];

        int centerIdx = toSplit.size / 2;

        int toSplitIdx = -1;
        for (int newNodeIdx = 0; newNodeIdx < centerIdx; ++newNodeIdx) {
            toSplitIdx = centerIdx + 1 + newNodeIdx;

            newNode.keys[newNodeIdx] = toSplit.keys[toSplitIdx];
            toSplit.keys[toSplitIdx] = null;

            newNode.children[newNodeIdx] = toSplit.children[toSplitIdx];
            toSplit.children[toSplitIdx] = null;
        }

        newNode.children[centerIdx] = toSplit.children[toSplitIdx + 1];

        newNode.size = centerIdx;
        toSplit.size = centerIdx;

        for (int parentIdx = parent.size; parentIdx > fullChildIdx; --parentIdx) {
            parent.keys[parentIdx] = parent.keys[parentIdx - 1];
            parent.children[parentIdx + 1] = parent.children[parentIdx];
        }
        parent.keys[fullChildIdx] = toSplit.keys[centerIdx];
        toSplit.keys[centerIdx] = null;

        parent.children[fullChildIdx + 1] = newNode;

        parent.size = parent.size + 1;
    }
}