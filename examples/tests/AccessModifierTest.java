package examples.tests;

// TODO: Inner private and protected classes

class PackagePrivateClass {
}

public class AccessModifierTest {
    public static void main(String[] args) {
        new PackagePrivateClass(); // Use the class somewhere, otherwise it will not be loaded at runtime

        System.out.println("AccessModifierTest");
    }
}