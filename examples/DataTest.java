class ExampleClass {
}

class DataContainer {
    public boolean booleanValue = true;
    public byte byteValue = 127;
    public char charValue = 'a';
    public double doubleValue = 3.141592653589793;
    public float floatValue = 3.141592f;
    public int intValue = 2147483647;
    public long longValue = 9223372036854775807L;
    public short shortValue = 32767;

    public ExampleClass classReference = new ExampleClass();
    public Object nullReference = null;
}

public class DataTest {
    public static void main(String[] args) {
        var data = new DataContainer();
    }
}