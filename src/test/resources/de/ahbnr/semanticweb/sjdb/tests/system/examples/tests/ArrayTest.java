package examples.tests;

public class ArrayTest {
    private static void testArraySize() {
        var array = new int[]{
                0, 1, 2
        };

        System.out.println("Test: testArraySize");
    }

    private static void testArrayContentClosure() {
        var array = new Integer[]{
                null, null, null
        };

        System.out.println("Test: testArrayContentClosure");
    }

    public static void main(String[] args) {
        testArraySize();
        testArrayContentClosure();
    }
}