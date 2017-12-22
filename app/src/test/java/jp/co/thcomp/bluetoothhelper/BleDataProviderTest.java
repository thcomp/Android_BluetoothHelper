package jp.co.thcomp.bluetoothhelper;

import org.junit.Assert;
import org.junit.Test;

public class BleDataProviderTest {
    private static final String[] TestJsons = {
            "{\"test\": [{\"test1\": 1,\"test2\": \"2\"},{\"test1\": 3,\"test2\": \"4\"}],\"test2\": {\"test3\": \"three\",\"test4\": 4}}",
            "{\"test\":[{\"test1\": 1,\"test2\": \"2\"},{\"test1\": 3,\"test2\": \"4\"},{\"test1\": 3,\"test2\": \"4\"},{\"test1\": 5,\"test2\": \"6\"},{\"test1\": 7,\"test2\": \"eight\"}],\"test2\": {\"test3\": \"three\",\"test4\": 4},\"test3\": {\"test4\": \"four\",\"test5\": 5}}",
            "{\"test\": [{\"test1\": 11,\"test2\": \"12\"},{\"test1\": 13,\"test2\": \"14\"}],\"test2\": {\"test3\": \"thirteen\",\"test4\": 14}}",
    };
    private static final int[] TestMTUs = {20, 21, 22};

    @Test
    public void test1() throws Exception {
        /*
            正常系試験
                BleSendDataProviderにJSON渡して、BleReceiveDataProviderで元に戻す
         */
        for (String testJson : TestJsons) {
            for (int mtu : TestMTUs) {
                BleSendDataProvider sendDataProvider = new BleSendDataProvider(testJson.getBytes());
                BleReceiveDataProvider recvDataProvider = new BleReceiveDataProvider();
                byte[] buffer = null;

                for (int i = 0; (buffer = sendDataProvider.getPacket(mtu, i)) != null; i++) {
                    Assert.assertFalse(recvDataProvider.isCompleted());
                    recvDataProvider.addPacket(buffer);
                }

                Assert.assertTrue(recvDataProvider.isCompleted());
                Assert.assertArrayEquals(recvDataProvider.getData(), testJson.getBytes());
            }
        }
    }

    @Test
    public void test2() throws Exception {
        /*
            データ混在(最初に設定パケット)
         */
        for (int i = 0, size = TestJsons.length; i < (size - 1); i++) {
            for (int mtu : TestMTUs) {
                BleSendDataProvider[] sendDataProviders = new BleSendDataProvider[]{
                        new BleSendDataProvider(TestJsons[i].getBytes()),
                        new BleSendDataProvider(TestJsons[i + 1].getBytes())};
                BleReceiveDataProvider recvDataProvider = new BleReceiveDataProvider();
                byte[] buffer = null;

                for (int j = 0; j < 200; j++) {
                    if (j == 0) {
                        // 設定パケットのときだけ、addPacketする順番を変更し、間違った設定パケットを指定されるようにする
                        for (int k = 0; k < sendDataProviders.length; k++) {
                            buffer = sendDataProviders[k].getPacket(mtu, j);
                            if (buffer != null) {
                                Assert.assertFalse(recvDataProvider.isCompleted());
                                recvDataProvider.addPacket(buffer);
                            }
                        }
                    } else {
                        for (int k = sendDataProviders.length - 1; k >= 0; k--) {
                            buffer = sendDataProviders[k].getPacket(mtu, j);
                            if (buffer != null) {
                                if (sendDataProviders[k].getMessageId() == recvDataProvider.getMessageId()) {
                                    Assert.assertFalse(recvDataProvider.isCompleted());
                                }
                                recvDataProvider.addPacket(buffer);
                            }
                        }
                    }
                }

                Assert.assertTrue(recvDataProvider.isCompleted());
                Assert.assertArrayEquals(recvDataProvider.getData(), TestJsons[i].getBytes());
            }
        }
    }

    @Test
    public void test3() throws Exception {
        /*
            データ混在(最後に設定パケット)
         */
        for (int i = 0, size = TestJsons.length; i < (size - 1); i++) {
            for (int mtu : TestMTUs) {
                BleSendDataProvider[] sendDataProviders = new BleSendDataProvider[]{
                        new BleSendDataProvider(TestJsons[i].getBytes()),
                        new BleSendDataProvider(TestJsons[i + 1].getBytes())};
                BleReceiveDataProvider recvDataProvider = new BleReceiveDataProvider();
                byte[] buffer = null;

                for (int j = 200; j >= 0; j--) {
                    if (j == 0) {
                        // 設定パケットのときだけ、addPacketする順番を変更し、間違った設定パケットを指定されるようにする
                        for (int k = 0; k < sendDataProviders.length; k++) {
                            buffer = sendDataProviders[k].getPacket(mtu, j);
                            if (buffer != null) {
                                recvDataProvider.addPacket(buffer);
                            }
                        }
                    } else {
                        for (int k = sendDataProviders.length - 1; k >= 0; k--) {
                            buffer = sendDataProviders[k].getPacket(mtu, j);
                            if (buffer != null) {
                                Assert.assertFalse(recvDataProvider.isCompleted());
                                recvDataProvider.addPacket(buffer);
                            }
                        }
                    }
                }

                int longestMessageIndex = 0;
                int tempLongestMessageSize = 0;
                for (int msgIndex = 0, msgIndexSize = sendDataProviders.length; msgIndex < msgIndexSize; msgIndex++) {
                    if (tempLongestMessageSize < sendDataProviders[msgIndex].getData().length) {
                        tempLongestMessageSize = sendDataProviders[msgIndex].getData().length;
                        longestMessageIndex = msgIndex;
                    }
                }

                Assert.assertTrue(recvDataProvider.isCompleted());
                Assert.assertArrayEquals(recvDataProvider.getData(), TestJsons[i + longestMessageIndex].getBytes());
            }
        }
    }
}