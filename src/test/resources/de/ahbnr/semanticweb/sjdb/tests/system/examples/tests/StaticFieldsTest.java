package examples.tests;

class SFTMyClass {
    public static int staticIntField = 42;
    public static String staticStringField = "Lorem Ipsum";

    public int nonStaticField = 1337;
}

public class StaticFieldsTest {
    public static void main(String[] args) {
        var myInstance = new SFTMyClass();

        System.out.println("Hello Static Fields!");
    }
}