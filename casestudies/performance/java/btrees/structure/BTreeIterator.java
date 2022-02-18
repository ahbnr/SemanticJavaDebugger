package btrees.structure;

import java.util.Iterator;
import java.util.Stack;
import java.lang.Override;
import java.lang.RuntimeException;


class BTreeIterator<K extends Comparable<? super K>> implements Iterator<K> {
    private static class NodeTraversalState<K extends Comparable<? super K>> {
        BTreeNode<K> node;

        int idx;

        NodeTraversalState(
                BTreeNode<K> node,
                int idx
        ) {
            this.node = node;
            this.idx = idx;
        }
    }

    private Stack<NodeTraversalState<K>> stack;

    BTreeIterator(BTreeNode<K> root) {
        this.stack = new Stack<NodeTraversalState<K>>();

        if (root != null) {
            this.stack.push(new NodeTraversalState<K>(root, -1));
        }
    }

    @Override
    public boolean hasNext() {
        return !this.stack.isEmpty();
    }

    @Override
    public K next() {
        var currentState = this.stack.pop();

        var firstChild = currentState.node.children[0];
        var isLeaf = firstChild == null;

        K toReturn = null;

        if (isLeaf) {
            // then yield all keys
            if (currentState.idx < currentState.node.size - 2) {
                this.stack.push(
                        new NodeTraversalState<K>(currentState.node, currentState.idx + 1)
                );
            }

            toReturn = currentState.node.keys[currentState.idx + 1];
        } else {
            if (currentState.idx < 0) {
                this.stack.push(
                        new NodeTraversalState<K>(currentState.node.children[0], -1)
                );

                this.stack.push(
                        new NodeTraversalState<K>(currentState.node, 0)
                );

                toReturn = next(); // first iteration endpoint
            } else {
                // add right child
                this.stack.push(
                        new NodeTraversalState<K>(currentState.node.children[currentState.idx + 1], -1)
                );

                if (currentState.idx < currentState.node.size - 1) {
                    this.stack.push(
                            new NodeTraversalState<K>(currentState.node, currentState.idx + 1)
                    );
                }

                // yield the current key
                toReturn = currentState.node.keys[currentState.idx];
            }
        }

        if (toReturn == null) {
            throw new RuntimeException("Trying to continue iteration after end of tree content.");
        }

        return toReturn; // second iteration endpoint
    }
}