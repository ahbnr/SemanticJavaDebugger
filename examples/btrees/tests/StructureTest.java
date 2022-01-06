package examples.btrees.tests;

import java.lang.SuppressWarnings;
import java.lang.String;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Array;

import examples.btrees.BTree;
import examples.btrees.BTreeNode;

public class StructureTest {
    private static BTreeNode<Integer> newNode() {
        return new BTreeNode<Integer>(Integer.class);
    }

    @SuppressWarnings("unchecked")
    private static BTreeNode<Integer>[] newNodeArray(int size) {
        var nodeArray = (BTreeNode<Integer>[]) Array.newInstance(BTreeNode.class, size);
        for (int i = 0; i < nodeArray.length; ++i) {
            nodeArray[i] = newNode();
        }

        return nodeArray;
    }

    public static void NodeTypeTests() {
        var root = newNode();
        root.children[0] = newNode();

        var middle = root.children[0];
        middle.children[0] = newNode();

        var leaf = middle.children[0];

        System.out.println("Test point: NodeTypeTests");
    }

    public static void ConditionOneSuccess() {
        var root = newNode();
        root.children = newNodeArray(2 * BTree.order);

        System.out.println("Test point: ConditionOneSuccess");
    }

    public static void ConditionOneError() {
        var root = newNode();
        root.children = newNodeArray(2 * BTree.order + 1);

        System.out.println("Test point: ConditionOneError");
    }

    public static void ConditionTwoSuccess() {
        var root = newNode();
        root.children[0] = newNode();
        root.children[1] = newNode();

        var leftMiddle = root.children[0];
        leftMiddle.children[0] = newNode();
        leftMiddle.children[1] = newNode();

        System.out.println("Test point: ConditionTwoSuccess");
    }

    public static void ConditionTwoError() {
        var root = newNode();
        root.children[0] = newNode();
        root.children[1] = newNode();

        var leftMiddle = root.children[0];
        leftMiddle.children[0] = newNode();

        System.out.println("Test point: ConditionTwoError");
    }

    public static void ConditionThreeSuccess() {
        var root1 = newNode();
        root1.children[0] = newNode();
        root1.children[1] = newNode();

        var root2 = newNode();

        System.out.println("Test point: ConditionThreeSuccess");
    }

    public static void ConditionThreeError() {
        var root = newNode();
        root.children[0] = newNode();

        var leaf = root.children[0];

        System.out.println("Test point: ConditionThreeError");
    }

    public static void ValidNodeConditionSuccess() {
        var root = newNode();
        root.children[0] = newNode();
        root.children[1] = newNode();

        var leftMiddle = root.children[0];
        leftMiddle.children[0] = newNode();
        leftMiddle.children[1] = newNode();

        var rightMiddle = root.children[0];
        rightMiddle.children[0] = newNode();
        rightMiddle.children[1] = newNode();

        System.out.println("Test point: ValidNodeConditionSuccess");
    }

    public static void ValidNodeConditionError() {
        var root = newNode();
        root.children[0] = newNode();

        var middle = root.children[0];
        middle.children[0] = newNode();

        System.out.println("Test point: ValidNodeConditionError");
    }

    public static void main(String[] args) {
        NodeTypeTests();

        ConditionOneSuccess();
        ConditionOneError();

        ConditionTwoSuccess();
        ConditionTwoError();

        ConditionThreeSuccess();
        ConditionThreeError();

        ValidNodeConditionSuccess();
        ValidNodeConditionError();
    }
}