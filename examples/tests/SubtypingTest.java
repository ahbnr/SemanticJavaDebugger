package examples.tests;

interface TopInterface1 {
    public void topInterface1Method();
}

interface TopInterface2 {
    public void topInterface2Method();
}

interface MidInterface extends TopInterface1, TopInterface2 {
    public void midInterfaceMethod();
}

interface ISameMethodName1 {
    void method();
}

interface ISameMethodName2 {
    void method();
}

class SameMethodName implements ISameMethodName1, ISameMethodName2 {
    @java.lang.Override
    public void method() {
    }
}

class Top {
    public String topField = "topField";
}

class Mid extends Top implements MidInterface {
    public String midField = "midField";

    public void midMethod() {
    }

    public void topInterface1Method() {
    }

    public void topInterface2Method() {
    }

    public void midInterfaceMethod() {
    }
}

class Bot1 extends Mid {
    public String bot1Field = "bot1Field";
}

class Bot2 extends Mid {
    public String bot2Field = "bot2Field";
}

public class SubtypingTest {
    public static void main(String[] args) {
        var top = new Top();
        var mid = new Mid();
        var bot1 = new Bot1();
        var bot2 = new Bot2();

        System.out.println("Hello Type Hierarchy!");
    }
}