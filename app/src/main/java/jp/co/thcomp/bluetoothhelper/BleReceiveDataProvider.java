package jp.co.thcomp.bluetoothhelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public class BleReceiveDataProvider extends BleDataProvider {
    public static final int AddPacketResultSuccess = -1;
    public static final int AddPacketResultAlreadyFinished = -2;
    public static final int AddPacketResultNoData = -3;
    private boolean mReceiveDataFinish = false;
    private byte[][] mReceiveDataArray;
    private int mLeftPacketCount = 0;
    private int mDataSize;
    private Short mReservedMessageId = null;
    private ArrayList<byte[]> mReservedPacketList = new ArrayList<>();

    /**
     * @param packetData
     * @return AddPacketResultAlreadyFinished: 既に完了済みのメッセージへの追加(追加失敗)
     * AddPacketResultSuccess: 追加成功
     * 0-ShortMax: 別のメッセージを追加している(追加失敗)
     */
    public int addPacket(byte[] packetData) {
        int ret = AddPacketResultSuccess;

        if (packetData != null && packetData.length > 0) {
            if (!mReceiveDataFinish) {
                ByteBuffer tempBufferForShort = ByteBuffer.allocate(Short.SIZE / Byte.SIZE);
                ByteBuffer tempBufferForInt = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE);

                // 0-1バイト：メッセージID(ShortMax上限且つPeripheralからの送信順番を示すが値は循環する)
                tempBufferForShort.position(0);
                tempBufferForShort.put(packetData, 0, LengthMessageID);
                short messageId = tempBufferForShort.getShort(0);

                // 2-5バイト：パケットサイズ、MTUサイズ以下の値が設定される
                tempBufferForInt.position(0);
                tempBufferForInt.put(packetData, IndexPacketSize, LengthPacketSize);
                int packetSize = tempBufferForInt.getInt(0);

                // 6-9バイト: パケットポジション、0は設定パケット、1以上の値が設定されている場合はデータパケット
                tempBufferForInt.position(0);
                tempBufferForInt.put(packetData, IndexPacketPosition, LengthPacketPosition);
                int packetPosition = tempBufferForInt.getInt(0);

                if (packetPosition == 0) {
                    if (mMessageId == null) {
                        boolean matchMessageId = true;
                        if (mReservedMessageId != null) {
                            // 既にリザーブされたMessageIdがあるので、それ以外の設定パケットは受け付けない
                            if (messageId != mReservedMessageId) {
                                matchMessageId = false;
                            }
                        }

                        if (matchMessageId) {
                            mMessageId = messageId;

                            // 設定パケット
                            // 10-13バイト：パケット数(設定パケットも含む)(IntMax上限)
                            tempBufferForInt.position(0);
                            tempBufferForInt.put(packetData, IndexPacketCount, LengthPacketCount);
                            mLeftPacketCount = tempBufferForInt.getInt(0) - 1;
                            mReceiveDataArray = new byte[mLeftPacketCount][];

                            // 14-17バイト：データサイズ(IntMax上限)
                            tempBufferForInt.position(0);
                            tempBufferForInt.put(packetData, IndexDataSize, LengthDataSize);
                            mDataSize = tempBufferForInt.getInt(0);

                            if (mReservedMessageId != null && mReservedPacketList.size() > 0) {
                                // 保留されているメッセージを展開
                                for (byte[] reservedPacketData : mReservedPacketList) {
                                    addPacket(reservedPacketData);
                                }
                            }

                            mReservedMessageId = null;
                            mReservedPacketList.clear();
                        }
                    } else {
                        // 別のメッセージパケットを追加しようとしているので、新しい方のメッセージIDを返却
                        ret = messageId;
                    }
                } else {
                    if (mMessageId == null) {
                        if (mReservedMessageId == null) {
                            mReservedMessageId = messageId;
                        }

                        if (mReservedMessageId == messageId) {
                            // 設定パケットが未だないので保留リストに
                            mReservedPacketList.add(packetData);
                        }
                    } else if (mMessageId == messageId) {
                        // データパケット
                        if (mReceiveDataArray != null) {
                            mLeftPacketCount--;

                            // 10 バイト：次のパケットがあるかのフラグ、０：次パケットなし、１：次パケットあり
                            tempBufferForInt.position(0);
                            tempBufferForInt.put(packetData, IndexExistNextPacket, LengthExistNextPacket);
                            int existNextPacket = tempBufferForInt.getInt(0);

                            // 以後、0-3バイトに記載されていたサイズ - 9バイトを引算したサイズだけデータが格納
                            mReceiveDataArray[packetPosition - 1] = Arrays.copyOfRange(packetData, IndexDataStartPosition, packetSize);

                            if ((mLeftPacketCount == 0) || (existNextPacket == NotExistNextPacket)) {
                                // 一旦0に残パケット数を0にして、受信状況に合わせて正しい値にする
                                mLeftPacketCount = 0;
                                for (int i = 0, size = mReceiveDataArray.length; i < size; i++) {
                                    if (mReceiveDataArray[i] == null) {
                                        mLeftPacketCount++;
                                    }
                                }

                                if (mLeftPacketCount == 0) {
                                    mReceiveDataFinish = true;
                                }
                            }
                        }
                    } else {
                        ret = messageId;
                    }
                }
            } else {
                ret = AddPacketResultAlreadyFinished;
            }
        } else {
            ret = AddPacketResultNoData;
        }

        return ret;
    }

    public boolean isCompleted() {
        return mReceiveDataFinish;
    }

    @Override
    public byte[] getData() {
        byte[] ret = null;
        if (mReceiveDataFinish) {
            if (mData == null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                try {
                    for (int i = 0, size = mReceiveDataArray.length; i < size; i++) {
                        stream.write(mReceiveDataArray[i]);
                    }
                    mData = stream.toByteArray();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            ret = super.getData();
        }
        return ret;
    }

    @Override
    public Short getMessageId() {
        if (mReservedMessageId != null && mMessageId == null) {
            return mReservedMessageId;
        } else {
            return super.getMessageId();
        }
    }
}
