package examples.twothreetree;

class Pair<K, V> {
    public K key;
    public V value;

    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }
}

class Node<K, V> {
    public Pair<K, V> dataL;
    public Pair<K, V> dataR;

    public Node<K, V> childL;
    public Node<K, V> childM;
    public Node<K, V> childR;

    public Node<K, V> parent;
}


public class TwoThreeTree {
    private static Node<Integer, String> buildValid() {
        var root = new Node<Integer, String>();

        var leaf1 = new Node<Integer, String>();
        leaf1.dataL = new Pair<>(0, "Lorem");
        leaf1.parent = root;
        root.childL = leaf1;

        root.dataL = new Pair<>(1, "ipsum");

        var leaf2 = new Node<Integer, String>();
        leaf2.dataL = new Pair<>(2, "dolor");
        leaf2.parent = root;
        root.childM = leaf2;

        return root;
    }

    private static Node<Integer, String> buildFaulty() {
        var root = new Node<Integer, String>();

        var leafL = new Node<Integer, String>();
        leafL.dataL = new Pair<>(0, "Lorem");
        leafL.parent = root;
        root.childL = leafL;

        root.dataL = new Pair<>(1, "ipsum");

        var leafM = new Node<Integer, String>();
        leafM.dataL = new Pair<>(2, "dolor");
        leafM.parent = root;
        root.childM = leafM;

        // root.dataR = new Pair<>(3, "sit");

        var leafR = new Node<Integer, String>();
        leafR.dataL = new Pair<>(4, "amet");
        leafR.parent = root;
        root.childR = leafR;

        return root;
    }

    public static void main(String[] args) {
        buildValid();
        buildFaulty();
    }
}