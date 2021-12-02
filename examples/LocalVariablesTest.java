package examples;

class MyClass {
    public void basic() {
        int localVariable = 42;

        System.out.println("basic.");
    }

    public void scopes() {
        {
            int localVariable = 42;
            System.out.println(localVariable);
        }
        {
            int localVariable = 1337;
            System.out.println(localVariable);
        }

        System.out.println("scopes.");
    }

    public void removedVar() {
        {
            int localVariable = 42; // the compiler will remove this one
        }
        {
            int localVariable = 12;

            System.out.println(localVariable);
        }

        System.out.println("removed var.");
    }
}

public class LocalVariablesTest {
    public static void main(String[] args) {
        var myInstance = new MyClass();

        myInstance.basic();
        myInstance.scopes();
        myInstance.removedVar();
    }
}