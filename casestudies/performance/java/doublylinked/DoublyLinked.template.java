class Node {
    Node previous;
    Object data;
    Node next;

    Node(Object data) {
        this.data = data;
    }

    Node append(Object data) {
        if (this.next != null) {
            throw new RuntimeException("Can only append at the end of the list.");
        }

        var nextNode = new Node(data);
        nextNode.previous = this;
        nextNode.next = null;
        this.next = nextNode;

        return nextNode;
    }
}

public class DoublyLinked {
    public static void main(String[] args) {
        Node firstNode = null;
        {
            Node currentNode = null;
            for (int i = 0; i < {{num_nodes}}; ++i) {
                var data = Integer.valueOf(i);

                if (currentNode == null) {
                    firstNode = new Node(data);
                    currentNode = firstNode;
                } else
                    currentNode = currentNode.append(data);

                // Break every second node
                if (i % 2 == 1) {
                    currentNode.previous = null;
                }
            }
        }

        System.out.println("Nodes created.");
    }
}
