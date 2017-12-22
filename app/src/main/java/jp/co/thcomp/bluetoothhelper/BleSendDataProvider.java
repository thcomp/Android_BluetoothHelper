package jp.co.thcomp.bluetoothhelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class BleSendDataProvider extends BleDataProvider {
    private static short sMessageId = 0;
    private static final Object sSemaphore = new Object();

    public BleSendDataProvider(byte[] data) {
        mData = data;

        synchronized (sSemaphore) {
            mMessageId = sMessageId;

            if (sMessageId == Short.MAX_VALUE) {
                sMessageId = 0;
            } else {
                sMessageId++;
            }
        }
    }

    public byte[] getPacket(int mtu, int packetIndex) {
        byte[] ret = null;
        ByteArrayOutputStream tempRet = null;
        int packetCount = 1/*設定パケット*/ + (int) Math.ceil(((float) mData.length) / (mtu - LengthDataPacketHeaderSize));

        try {
            if (packetIndex == 0) {
                // 設定パケット
                tempRet = new ByteArrayOutputStream();

                ByteBuffer tempBufferForShort = ByteBuffer.allocate(Short.SIZE / Byte.SIZE);
                ByteBuffer tempBufferForInt = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE);

                // 0-1バイト：メッセージID(ShortMax上限且つPeripheralからの送信順番を示すが値は循環する)
                tempBufferForShort.asShortBuffer().put(mMessageId);
                tempRet.write(tempBufferForShort.array());

                // 2-5バイト：パケットサイズ、MTUサイズ以下の値が設定される
                tempBufferForInt.asIntBuffer().put(LengthSettingPacketHeaderSize);
                tempRet.write(tempBufferForInt.array());

                // 6-9バイト: パケットポジション、0は設定パケット、1以上の値が設定されている場合はデータパケット
                tempBufferForInt.asIntBuffer().put(0);
                tempRet.write(tempBufferForInt.array());

                // 10-13バイト：パケット数(設定パケットも含む)
                tempBufferForInt.asIntBuffer().put(packetCount);
                tempRet.write(tempBufferForInt.array());

                // 14-17バイト：データサイズ(IntMax上限)
                tempBufferForInt.asIntBuffer().put(mData.length);
                tempRet.write(tempBufferForInt.array());
            } else {
                if (packetIndex < packetCount) {
                    tempRet = new ByteArrayOutputStream();

                    byte existNextPacket = packetIndex == (packetCount - 1) ? NotExistNextPacket : ExistNextPacket;
                    int maxDataSize = mtu - LengthDataPacketHeaderSize;
                    int dataSize = existNextPacket == ExistNextPacket ?
                            maxDataSize :   //  次のパケットが存在するので最大量
                            mData.length % maxDataSize == 0 ? // 最後のパケットなので最大量の余算か余りが存在しないときは、最大量
                                    maxDataSize :
                                    mData.length % maxDataSize;
                    int packetSize = LengthDataPacketHeaderSize + dataSize;

                    ByteBuffer tempBufferForShort = ByteBuffer.allocate(Short.SIZE / Byte.SIZE);
                    ByteBuffer tempBufferForInt = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE);

                    // 0-1バイト：メッセージID(ShortMax上限且つPeripheralからの送信順番を示すが値は循環する)
                    tempBufferForShort.asShortBuffer().put(mMessageId);
                    tempRet.write(tempBufferForShort.array());

                    // 2-5バイト：パケットサイズ、MTUサイズ以下の値が設定される
                    tempBufferForInt.asIntBuffer().put(packetSize);
                    tempRet.write(tempBufferForInt.array());

                    // 6-9バイト: パケットポジション、0は設定パケット、1以上の値が設定されている場合はデータパケット
                    tempBufferForInt.asIntBuffer().put(packetIndex);
                    tempRet.write(tempBufferForInt.array());

                    // 10バイト：次のパケットがあるかのフラグ、０：次パケットなし、１：次パケットあり
                    tempRet.write(existNextPacket);

                    // データ
                    int packetIndexInData = packetIndex - 1/*packetIndexには設定パケット分も数に含まれているのでデータとしては1引算*/;
                    tempRet.write(mData,
                            packetIndexInData * maxDataSize,
                            dataSize);
                }
            }
        } catch (IOException e) {
            tempRet = null;
        } finally {
            if (tempRet != null) {
                ret = tempRet.toByteArray();

                try {
                    tempRet.close();
                } catch (IOException e) {
                    // 処理なし
                }
            }
        }

        return ret;
    }
}
