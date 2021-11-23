class ExampleClass {
}

class DataContainer {
    public boolean booleanField = true;
    public byte byteField = 127;
    public char charField = 'a';
    public double doubleField = 3.141592653589793;
    public float floatField = 3.141592f;
    public int intField = 2147483647;
    public long longField = 9223372036854775807L;
    public short shortField = 32767;

    public ExampleClass classReferenceField = new ExampleClass();
    public Object nullReferenceField = null;
}

public class DataTest {
    private static void fieldsTest() {
        var data = new DataContainer();
    }

    private static void varsTest() {
        boolean booleanVar = true;
        byte byteVar = 127;
        char charVar = 'a';
        double doubleVar = 3.141592653589793;
        float floatVar = 3.141592f;
        int intVar = 2147483647;
        long longVar = 9223372036854775807L;
        short shortVar = 32767;

        ExampleClass classReferenceVar = new ExampleClass();
        Object nullReferenceVar = null;
    }

    public static void main(String[] args) {
        fieldsTest();
        varsTest();
    }
}