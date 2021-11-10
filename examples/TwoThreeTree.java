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
    public static void main(String[] args) {
        var leaf1 = new Node<Integer, String>();
        leaf1.dataL = new Pair<>(0, "Lorem");
        leaf1.dataR = new Pair<>(1, "ipsum");

        var leaf2 = new Node<Integer, String>();
        leaf2.dataL = new Pair<>(3, "sit");

        // Root is a two-node
        var root = new Node<Integer, String>();
        root.childL = leaf1;
        root.dataL = new Pair<>(2, "dolor");
        root.childM = leaf2;

        leaf1.parent = root;
        leaf2.parent = root;
    }
}