package examples.tests;

class OuterClass {
    class InnerClass {

    }

    static class StaticInnerClass {

    }
}

public class ClassDefinitionsTest {
    public static void main(String[] args) {
        var outer = new OuterClass(); // Use the class somewhere, otherwise it will not be loaded at runtime
        var inner = outer.new InnerClass();
        var staticInner = new OuterClass.StaticInnerClass();

        System.out.println("ClassDefinitionsTest");
    }
}