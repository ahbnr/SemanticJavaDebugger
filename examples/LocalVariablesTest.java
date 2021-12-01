package examples;

class MyClass {
    public void firstMethod() {
        int localVariable = 42;

        System.out.println("firstMethod.");
    }
}

public class LocalVariablesTest {
    public static void main(String[] args) {
        var myInstance = new MyClass();

        myInstance.firstMethod();
    }
}