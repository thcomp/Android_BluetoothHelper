package jp.co.thcomp.bluetoothhelper;

/**
 * 20バイトは必須
 * 0-1バイト：メッセージID(ShortMax上限且つPeripheralからの送信順番を示すが値は循環する)
 * 2-5バイト：パケットサイズ、MTUサイズ以下の値が設定される(IntMax上限)
 * 6-9バイト: パケットポジション、0は設定パケット、1以上の値が設定されている場合はデータパケット(IntMax上限)
 * [設定パケット]
 * 10-13バイト：パケット数(設定パケットも含む)(IntMax上限)
 * 14-17バイト：データサイズ(IntMax上限)
 * [データパケット]
 * 10バイト：次のパケットがあるかのフラグ、０：次パケットなし、１：次パケットあり
 * 以後、2-5バイトに記載されていたサイズ - 11バイトを引算したサイズだけデータが格納
 * <p>
 * MTUが20バイトの場合、最大(IntMax - 1) * (20 - 11) = 17GB程度送信可能
 */
abstract public class BleDataProvider {
    protected static final int IndexMessageID = 0;
    protected static final int LengthMessageID = 2;
    protected static final int IndexPacketSize = IndexMessageID + LengthMessageID;
    protected static final int LengthPacketSize = 4;
    protected static final int IndexPacketPosition = IndexPacketSize + LengthPacketSize;
    protected static final int LengthPacketPosition = 4;

    private static final int LengthPacketHeaderSize = LengthMessageID + LengthPacketSize + LengthPacketPosition;

    // 設定パケット
    protected static final int IndexPacketCount = IndexPacketPosition + LengthPacketPosition;
    protected static final int LengthPacketCount = 4;
    protected static final int IndexDataSize = IndexPacketCount + LengthPacketCount;
    protected static final int LengthDataSize = 4;
    protected static final int LengthSettingPacketHeaderSize = LengthPacketHeaderSize + LengthPacketCount + LengthDataSize;

    // データパケット
    protected static final int IndexExistNextPacket = IndexPacketPosition + LengthPacketPosition;
    protected static final int LengthExistNextPacket = 1;
    protected static final int IndexDataStartPosition = IndexExistNextPacket + LengthExistNextPacket;
    protected static final int LengthDataPacketHeaderSize = LengthPacketHeaderSize + LengthExistNextPacket;

    protected static final byte NotExistNextPacket = 0;
    protected static final byte ExistNextPacket = 1;

    protected byte[] mData;
    protected Short mMessageId;

    public byte[] getData() {
        return mData;
    }

    public Short getMessageId() {
        return mMessageId;
    }
}
