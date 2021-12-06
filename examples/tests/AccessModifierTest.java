package examples.tests;

class PackagePrivateClass {
    PackagePrivateClass() {
        new PrivateClass(); // Use the class somewhere, otherwise it will not be loaded at runtime
        new ProtectedClass();
    }

    private static class PrivateClass {

    }

    protected static class ProtectedClass {

    }
}

public class AccessModifierTest {
    void packagePrivateMethod() {
    }

    public void publicMethod() {
    }

    protected void protectedMethod() {
    }

    private void privateMethod() {
    }

    public static void main(String[] args) {
        new PackagePrivateClass(); // Use the class somewhere, otherwise it will not be loaded at runtime

        System.out.println("AccessModifierTest");
    }
}