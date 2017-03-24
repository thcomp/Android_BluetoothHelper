package jp.co.thcomp.bluetoothhelper;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.*;

public class BufferedByteReaderTest {
    private static final byte[][] sTestDelimiterArray = {
            "\n".getBytes(),
            "\r".getBytes(),
            "\r\n".getBytes(),
    };
    private static final byte[][] sTestCase1LineArray = {
            "line no.1: aaaaaaaaaa".getBytes(),
            "line no.2: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes(),
            "line no.3: aaaaaaaaaa".getBytes(),
            "".getBytes(),
    };
    private static final byte[][] sTestCase2LineArray = {
            "line no.1: aaaaaaaaaa".getBytes(),
            "line no.2: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes(),
    };
    private static final byte[][] sTestCase3LineArray = sTestCase2LineArray;
    private static final byte[][] sTestCase4LineArray = sTestCase2LineArray;
    private static final byte[][] sTestCase5LineArray = sTestCase1LineArray;

    @Test
    public void testCase1() throws Exception {
        byte[][] testCaseLineArray = sTestCase1LineArray;

        for(int i=0, sizeI=sTestDelimiterArray.length; i<sizeI; i++){
            ByteArrayOutputStream testDataStream = new ByteArrayOutputStream();
            for(int j=0, sizeJ=testCaseLineArray.length; j<sizeJ; j++){
                testDataStream.write(testCaseLineArray[j]);
                testDataStream.write(sTestDelimiterArray[i]);
            }
            ByteArrayInputStream inputStream = new ByteArrayInputStream(testDataStream.toByteArray());

            for(int j=0, sizeJ=testCaseLineArray.length; j<sizeJ; j++){
                BufferedByteReader reader = new BufferedByteReader(inputStream);
                byte[] data = reader.readLine(sTestDelimiterArray[i]);
                assertTrue(Arrays.equals(data, testCaseLineArray[j]));
            }
            BufferedByteReader reader = new BufferedByteReader(inputStream);
            byte[] data = reader.readLine(sTestDelimiterArray[i]);
            assertTrue(data != null && data.length == 0);
        }
    }

    @Test
    public void testCase2() throws Exception {
        byte[][] testCaseLineArray = sTestCase2LineArray;
        Field sStoredByteBufferMapField = BufferedByteReader.class.getDeclaredField("sStoredByteBufferMap");
        sStoredByteBufferMapField.setAccessible(true);
        HashMap<InputStream, byte[]> sStoredByteBufferMap = (HashMap<InputStream, byte[]>) sStoredByteBufferMapField.get(null);

        for(int i=0, sizeI=sTestDelimiterArray.length; i<sizeI; i++){
            ByteArrayOutputStream testDataStream = new ByteArrayOutputStream();
            for(int j=0, sizeJ=testCaseLineArray.length; j<sizeJ; j++){
                if(j != 0){
                    testDataStream.write(sTestDelimiterArray[i]);
                }
                testDataStream.write(testCaseLineArray[j]);
            }
            ByteArrayInputStream inputStream = new ByteArrayInputStream(testDataStream.toByteArray());

            for(int j=0, sizeJ=testCaseLineArray.length - 1; j<sizeJ; j++){
                BufferedByteReader reader = new BufferedByteReader(inputStream);
                byte[] data = reader.readLine(sTestDelimiterArray[i]);
                assertTrue(Arrays.equals(data, testCaseLineArray[j]));
            }

            BufferedByteReader reader = new BufferedByteReader(inputStream);
            reader.close();

            assertFalse(sStoredByteBufferMap.containsKey(inputStream));
        }
    }

    @Test
    public void testCase3() throws Exception {
        byte[][] testCaseLineArray = sTestCase3LineArray;
        Field sStoredByteBufferMapField = BufferedByteReader.class.getDeclaredField("sStoredByteBufferMap");
        sStoredByteBufferMapField.setAccessible(true);
        HashMap<InputStream, byte[]> sStoredByteBufferMap = (HashMap<InputStream, byte[]>) sStoredByteBufferMapField.get(null);

        for(int i=0, sizeI=sTestDelimiterArray.length; i<sizeI; i++){
            ByteArrayOutputStream testDataStream = new ByteArrayOutputStream();
            for(int j=0, sizeJ=testCaseLineArray.length; j<sizeJ; j++){
                if(j != 0){
                    testDataStream.write(sTestDelimiterArray[i]);
                }
                testDataStream.write(testCaseLineArray[j]);
            }
            ByteArrayInputStream inputStream = new ByteArrayInputStream(testDataStream.toByteArray());

            for(int j=0, sizeJ=testCaseLineArray.length - 1; j<sizeJ; j++){
                BufferedByteReader reader = new BufferedByteReader(inputStream);
                byte[] data = reader.readLine(sTestDelimiterArray[i]);
                assertTrue(Arrays.equals(data, testCaseLineArray[j]));
            }

            BufferedByteReader.releaseStoredByteBuffer(inputStream);

            assertFalse(sStoredByteBufferMap.containsKey(inputStream));
        }
    }

    @Test
    public void testCase4() throws Exception {
        byte[][] testCaseLineArray = sTestCase4LineArray;

        for(int i=0, sizeI=sTestDelimiterArray.length; i<sizeI; i++){
            ByteArrayOutputStream testDataStream = new ByteArrayOutputStream();
            for(int j=0, sizeJ=testCaseLineArray.length; j<sizeJ; j++){
                if(j != 0){
                    testDataStream.write(sTestDelimiterArray[i]);
                }
                testDataStream.write(testCaseLineArray[j]);
            }
            ByteArrayInputStream inputStream = new ByteArrayInputStream(testDataStream.toByteArray());

            for(int j=0, sizeJ=testCaseLineArray.length - 1; j<sizeJ; j++){
                BufferedByteReader reader = new BufferedByteReader(inputStream);
                byte[] data = reader.readLine(sTestDelimiterArray[i]);
                assertTrue(Arrays.equals(data, testCaseLineArray[j]));
            }
        }

        BufferedByteReader.releaseAllStoredByteBuffer();

        Field sStoredByteBufferMapField = BufferedByteReader.class.getDeclaredField("sStoredByteBufferMap");
        sStoredByteBufferMapField.setAccessible(true);
        HashMap<InputStream, byte[]> sStoredByteBufferMap = (HashMap<InputStream, byte[]>) sStoredByteBufferMapField.get(null);
        assertTrue(sStoredByteBufferMap.size() == 0);
    }

    @Test
    public void testCase5() throws Exception {
        byte[][] testCaseLineArray = sTestCase5LineArray;

        for(int i=0, sizeI=sTestDelimiterArray.length; i<sizeI; i++){
            ByteArrayOutputStream testDataStream = new ByteArrayOutputStream();
            for(int j=0, sizeJ=testCaseLineArray.length; j<sizeJ; j++){
                if(j != 0){
                    testDataStream.write(sTestDelimiterArray[i]);
                }
                testDataStream.write(testCaseLineArray[j]);
            }

            byte[] data = testDataStream.toByteArray();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(testDataStream.toByteArray());
            BufferedByteReader reader = new BufferedByteReader(inputStream);
            Method findDelimiterMethod = BufferedByteReader.class.getDeclaredMethod("findDelimiter", byte[].class, int.class, byte[].class);
            findDelimiterMethod.setAccessible(true);

            for(int j=0, sizeJ=testCaseLineArray.length - 1; j<sizeJ; j++) {
                int findDelimiter = (int) findDelimiterMethod.invoke(reader, data, sTestDelimiterArray[i]);
                for(int k=0, sizeK=sTestDelimiterArray[i].length; k<sizeK; k++){
                    assertTrue(data[findDelimiter + k] == sTestDelimiterArray[i][k]);
                }

                data = Arrays.copyOfRange(data, findDelimiter + sTestDelimiterArray[i].length, data.length);
            }
        }
    }
}
