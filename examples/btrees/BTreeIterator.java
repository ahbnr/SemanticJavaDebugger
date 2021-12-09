package examples.btrees;

import java.util.Iterator;
import java.util.Stack;
import java.lang.Override;


class BTreeIterator<K extends Comparable<? super K>> implements Iterator<K> {
    private static class NodeTraversalState<K extends Comparable<? super K>> {
        BTreeNode<K> node;

        int childIdx;
        int keyIdx;

        boolean atChild;

        NodeTraversalState(
                BTreeNode<K> node
        ) {
            this.node = node;

            this.keyIdx = 0;

            if (node.children[0] != null) {
                childIdx = 0;
                atChild = true;
            } else {
                childIdx = 2 * BTree.order;
                atChild = false;
            }
        }
    }

    private NodeTraversalState<K> currentState;
    private Stack<NodeTraversalState<K>> stack;

    BTreeIterator(BTreeNode<K> root) {
        if (root != null) {
            this.currentState = new NodeTraversalState<K>(root);
            this.stack = new Stack<NodeTraversalState<K>>();
        } else {
            this.currentState = null;
            this.stack = null;
        }
    }

    @Override
    public boolean hasNext() {
        return currentState != null;
    }

    @Override
    public K next() {
        while (currentState != null) {
            K toReturn = null;

            if (currentState.atChild) {
                var newState = new NodeTraversalState<K>(currentState.node.children[currentState.childIdx]);

                currentState.atChild = false;
                ++currentState.childIdx;
                stack.push(currentState);

                currentState = newState;
            } else {
                if (currentState.keyIdx < currentState.node.size) {
                    toReturn = currentState.node.keys[currentState.keyIdx];

                    ++currentState.keyIdx;
                    if (currentState.childIdx < currentState.node.children.length && currentState.node.children[currentState.childIdx] != null) {
                        currentState.atChild = true;
                    }
                }

                while (currentState.keyIdx >= currentState.node.size) {
                    if (stack.empty()) {
                        currentState = null;
                        break;
                    } else {
                        currentState = stack.pop();
                    }
                }
            }

            if (toReturn != null) {
                return toReturn;
            }
        }

        throw new java.lang.RuntimeException("No more elements.");
    }
}